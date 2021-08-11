package ext.sim.agents;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import bgu.dcr.az.api.agt.*;
import bgu.dcr.az.api.ano.*;
import bgu.dcr.az.api.tools.*;

@Algorithm(name = "DSA", useIdleDetector = false)
public class DSAAgent extends SimpleAgent {
	@Variable(name="courseLimit", defaultValue="10", description="number of max. students per course")
	private int courseLimit = 30;
	
	private static int extra_courses;
	private static int num_agents;
	private Assignment cpa;
	
	private static boolean flag;
	
	private static LocalTime start_time;
	
	private List<String[]> courseCombinations = readCombinations("combinations.csv");
	private HashMap<String, Integer> coursesAssignments; 
	
	private static final Object counterLock = new Object();
	
	private void iterate() {
		mapAssignments();
		assignNewValue(find_max_value());
	}
			
	@Override
	public void start() {
		cpa = new Assignment();
		if(isFirstAgent()) {
			start_time = LocalTime.now();
			iterate();
		}
	}
	
	private boolean checkSelfAssignment(int val) {
		boolean flag = false;
		for(String str: courseCombinations.get(val)) { 
			if(this.coursesAssignments.get(str) != null && this.coursesAssignments.get(str) >= this.courseLimit) {
				flag = true;
			}
		}
		return flag;
	}
	
	private int checkMinAssignment(int val) {
		int exceed = 0;
		for(String str: courseCombinations.get(val)) { 
			if(this.coursesAssignments.get(str) != null && this.coursesAssignments.get(str) >= this.courseLimit) {
				exceed += 1;
			}
		}
		return exceed;
	}
	
//	private synchronized int find_max_value() {
//		Set<Integer> currentDomain = new HashSet<Integer>(getDomain());
//		int bestVal = -1;
//		int cost = -1;
//		while(!currentDomain.isEmpty()) {
//			int val = currentDomain.iterator().next();
//			currentDomain.remove(val);
//			if (cpa.calcAddedCost(getId(), val, getProblem()) > cost && !checkSelfAssignment(val)) {	
//				bestVal = val;
//				cost = cpa.calcAddedCost(getId(), val, getProblem());
//			}
//		}
//		if(bestVal == -1) {
//			flag = true;
//			bestVal = 0;
//		}
//		return bestVal;
//	}
	
	private synchronized int find_max_value() {
		Set<Integer> currentDomain = new HashSet<Integer>(getDomain());
		int bestVal = -1;
		int cost = -1;
		while(!currentDomain.isEmpty()) {
			int val = currentDomain.iterator().next();
			currentDomain.remove(val);
			if (cpa.calcAddedCost(getId(), val, getProblem()) > cost && !checkSelfAssignment(val)) {	
				bestVal = val;
				cost = cpa.calcAddedCost(getId(), val, getProblem());
			}
		}
		if(bestVal == -1) {
			int exceed = Integer.MAX_VALUE;
			currentDomain = new HashSet<Integer>(getDomain());
			while(!currentDomain.isEmpty()) {
				int val = currentDomain.iterator().next();
				currentDomain.remove(val);
				if (cpa.calcAddedCost(getId(), val, getProblem()) > cost && checkMinAssignment(val) <= exceed) {	
					bestVal = val;
					cost = cpa.calcAddedCost(getId(), val, getProblem());
					exceed = checkMinAssignment(val);
				}
			}
		}
		return bestVal;
	}
	
	@WhenReceived("ASSIGNMENT")
	public void handleAssignment(int i, int v) {
		cpa.assign(i, v);
	}
	
	@WhenReceived("START")
	public void handleStart() {
		iterate();
	}
	
	private synchronized void update() {
		synchronized(counterLock) {
			num_agents++;
		}
	}
	
	
	private List<String[]> readCombinations(String csv) {
		String line = "";  
		String splitBy = ",";  
		List<String[]> combinations = new ArrayList<>();
		try   
		{  
			BufferedReader br = new BufferedReader(new FileReader(csv));  
			while ((line = br.readLine()) != null) 
			{  
				combinations.add(line.split(splitBy));
			}
			br.close();
		}   
		catch (IOException e)   
		{  
			e.printStackTrace();  
		}
		return combinations;
	}
	
	private double gini_coef() {
		double coef = 0;
		double mean = 0;
		for(int i=0; i<num_agents; i++) {
			mean += cpa.calcAddedCost(i, cpa.getAssignment(i), getProblem());
		}
		mean /= num_agents;
		for(int i=0; i<num_agents; i++) {
			for(int j=0; j<num_agents; j++) {
				coef += Math.abs(cpa.calcAddedCost(i, cpa.getAssignment(i), getProblem()) - cpa.calcAddedCost(j, cpa.getAssignment(j), getProblem()));
			}
		}
		coef /= 2*Math.pow(num_agents, 2)*mean;
		return coef*100;
	}
	
	private void output(String filename) {
		int seconds = LocalTime.now().toSecondOfDay() - start_time.toSecondOfDay();
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true));
			String time = Integer.toString((int) seconds/3600) + ":" + Integer.toString((int) seconds/60) + ":" + Integer.toString(seconds%60);
			if(!flag)
				writer.write(String.join(",", time, Integer.toString(cpa.calcCost(getProblem())), Integer.toString(extra_courses), Double.toString(gini_coef())));
			else
				writer.write(String.join(",", "Time Out", "Infinity", "No Solution", "Zero"));
			writer.newLine();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void calculate_extra_courses() {
		for(String key: this.coursesAssignments.keySet()) {
			if(this.coursesAssignments.get(key) > courseLimit) {
				extra_courses += this.coursesAssignments.get(key) - courseLimit;
			}
		}
	}
	
	@Override
	public void onMailBoxEmpty() {
		try {
			finish(getSubmitedCurrentAssignment());
			update();
			if(isLastAgent()) {
				calculate_extra_courses();
				output("output_ " + Integer.toString(num_agents) + "agents_greedy_iterative.csv");
				clear();
			}
		}
		catch(Exception e) {}
		return;
	}
	
	private void clear() {
		extra_courses = 0;
		num_agents = 0;
	}
	
	private synchronized void mapAssignments() {
		coursesAssignments = new HashMap<>();
		for(int var: cpa.assignedVariables()) {
			for(String str: courseCombinations.get(cpa.getAssignment(var))) {
				if(this.coursesAssignments.get(str) == null) 
					this.coursesAssignments.put(str, 1);
				else 
					this.coursesAssignments.put(str, this.coursesAssignments.get(str)+1);
			}
		}
	}
	
	private synchronized void assignNewValue(int val) {
		cpa.assign(getId(), val);
		submitCurrentAssignment(val);	
		broadcast("ASSIGNMENT", getId(), val);
		if(!isLastAgent())
			send("START").toNextAgent();
	}
}
