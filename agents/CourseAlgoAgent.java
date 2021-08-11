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

@Algorithm(name = "CourseAlgo", useIdleDetector = false)
public class CourseAlgoAgent extends SimpleAgent {

	@Variable(name="it", defaultValue="100", description="number of iterations to perform")
	private int it = 100;

	@Variable(name="p1", defaultValue="0.8", description="probability of value change if assignment is valid")
	private double p1 = 0.8;

	private double p2 = 0;

	@Variable(name="courseLimit", defaultValue="10", description="number of max. students per course")
	private int courseLimit = 30;

	private static LocalTime start_time;

	private Random rnd = new Random();
	private Assignment bestCpa;
	private Assignment minCpa;
	private Assignment cpa;

	private static int minExceed;
	private int bestVal;
	private boolean flag;
	
	private static boolean done;
	private static boolean first;

	private List<String[]> courseCombinations = readCombinations("combinations.csv");
	private HashMap<String, Integer> coursesAssignments; 

	private static volatile int num_agents;
	
	private static final Object counterLock = new Object();


	private synchronized void init() {
		synchronized(counterLock) {
			if(!first) {
				done = false;
				first = true;
				bestVal = 0;
				num_agents = 0;
				minExceed = Integer.MAX_VALUE;
				start_time = LocalTime.now();
			}
		}
	}

	@Override
	public void start() {
		synchronized(counterLock) {
			if(!first) {
				init();
			}
		}
		cpa = new Assignment();
		minCpa = new Assignment();
		assignNewValue(chooseAtRandom());
	}

	private int chooseAtRandom() {
		return rnd.nextInt(getDomainSize());
	}

	@WhenReceived("ASSIGNMENT")
	public void handleAssignment(int i, int v) {
		cpa.assign(i, v);
	}

	private synchronized void update() {
		synchronized(counterLock) {
			num_agents++;
		}
	}

	/** ========================= Common Functions ========================= */

	private void mapAssignments() {
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

	private boolean checkAllAssignments() {
		for(int val: this.coursesAssignments.values()) {
			if(val > this.courseLimit)
				return false;
		}
		return true;
	}

	/** ========================= Max - Beta ========================= */

	private Set<Integer> validDomain(String key){
		Set<Integer> currentDomain = new HashSet<Integer>(getDomain());
		Set<Integer> newDomain = new HashSet<Integer>(getDomain());
		for(int value: currentDomain) { 
			for(String str: this.courseCombinations.get(value)) {
				if(str.equals(key)) {
					newDomain.remove(value);
					break;
				}
			}
		}
		return newDomain;
	}

	private Set<Integer> noValidDomain(){
		Set<Integer> newDomain = new HashSet<Integer>(getDomain());

		List<String> validCourses = new ArrayList<>();
		for(String str: this.coursesAssignments.keySet()) {
			if(this.coursesAssignments.get(str) <= courseLimit) 
				validCourses.add(str);
		}

		for(int value: getDomain()) { 
			int courses_contained = 0;
			for(String str: this.courseCombinations.get(value)) {
				if(validCourses.contains(str))
					courses_contained += 1;
			}
			if(courses_contained != validCourses.size())
				newDomain.remove(value);
		}
		return newDomain;
	}

	private String checkSelfAssignment(int val) {
		int max_beta = 0;
		String course_key = "";
		for(String str: courseCombinations.get(val)) { 
			if(this.coursesAssignments.get(str) != null && this.coursesAssignments.get(str) > max_beta && this.coursesAssignments.get(str) > this.courseLimit) {
				max_beta = this.coursesAssignments.get(str);
				course_key = str;
			}

		}
		return course_key;
	}
	
//	private int checkMinAssignment() {
//		int exceed = 0;
//		for(int var: cpa.assignedVariables()) {
//			for(String str: courseCombinations.get(cpa.getAssignment(var))) { 
//				if(this.coursesAssignments.get(str) != null && this.coursesAssignments.get(str) >= this.courseLimit) {
//					exceed += 1;
//				}
//			}
//		}
//		return exceed;
//	}

	/** ========================= Max - Beta ========================= */

	private synchronized void output(String filename) {
		synchronized(counterLock){
			if(num_agents == getNumberOfVariables() && !done){
				done = true;
				int seconds = LocalTime.now().toSecondOfDay() - start_time.toSecondOfDay();
				try {
					BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true));
					String time = Integer.toString((int) seconds/3600) + ":" + Integer.toString((int) seconds/60) + ":" + Integer.toString(seconds%60);
					writer.write(String.join(",", time, Integer.toString(bestCpa.calcCost(getProblem())), Integer.toString(minExceed), Double.toString(gini_coef())));
					writer.newLine();
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
//	private synchronized void bad_output(String filename) {
//		synchronized(counterLock) {
//			if(!done) {
//				done = true;
//				try {
//					BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true));
//					writer.write(String.join(",", "Time Out", "Infinity", "No Solution", "Zero"));
//					writer.newLine();
//					writer.close();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}
//		}
//	}
	
	private void clear() {
		num_agents = 0;
		first = false;
	}
	
	private double gini_coef() {
		double coef = 0;
		double mean = 0;
		for(int i=0; i<num_agents; i++) {
			mean += bestCpa.calcAddedCost(i, bestCpa.getAssignment(i), getProblem());
		}
		mean /= num_agents;
		for(int i=0; i<num_agents; i++) {
			for(int j=0; j<num_agents; j++) {
				coef += Math.abs(bestCpa.calcAddedCost(i, bestCpa.getAssignment(i), getProblem()) - bestCpa.calcAddedCost(j, bestCpa.getAssignment(j), getProblem()));
			}
		}
		coef /= 2*Math.pow(num_agents, 2)*mean;
		return coef*100;
	}
	
	private int calculate_extra_courses() {
		int extra_courses = 0;
		for(String key: this.coursesAssignments.keySet()) {
			if(this.coursesAssignments.get(key) > courseLimit) {
				extra_courses += this.coursesAssignments.get(key) - courseLimit;
			}
		}
		return extra_courses;
	}

	@Override
	public void onMailBoxEmpty() {

		if (getSystemTimeInTicks() >= it) {
			if(bestCpa == null)
				bestCpa = minCpa.deepCopy();
			update();
			finish(bestCpa.getAssignment(getId()));
			if(num_agents == getNumberOfVariables() && !done) {
				synchronized(counterLock){
					output("output_ " + Integer.toString(num_agents) + "_course_agents.csv");
					clear();
				}
			}
			return;
		}

		mapAssignments();

		String beta_key = "";
		flag = false;
		try {
			int assign = getSubmitedCurrentAssignment();
			beta_key = checkSelfAssignment(assign); 
		}
		catch(Exception e) {}

		if(!beta_key.equals("")) {
			flag = true;
			p2 = (double) (this.coursesAssignments.get(beta_key)-this.courseLimit) / this.coursesAssignments.get(beta_key); 
		}

		else if(checkAllAssignments()) {
			if(bestCpa == null || cpa.calcCost(getProblem()) > bestCpa.calcCost(getProblem())) {
				bestCpa = cpa.deepCopy();
			}
		}
		
		if(minCpa == null || calculate_extra_courses() <= minExceed) {
			if(cpa.calcCost(getProblem()) > minCpa.calcCost(getProblem())) {
				minCpa = cpa.deepCopy();
				minExceed = calculate_extra_courses();
			}
		}

		Set<Integer> currentDomain = new HashSet<Integer>(getDomain());

		if(flag && rnd.nextDouble() < p2) {
			int cost = -1;
			int values = 0;
			for(int val: coursesAssignments.values()) {
				if(val <= courseLimit)
					values += 1;
			}
			if(values >= courseCombinations.get(0).length) {
				currentDomain = validDomain(beta_key);
				while(!currentDomain.isEmpty()) {
					int val = currentDomain.iterator().next();
					currentDomain.remove(val);
					if (cpa.calcAddedCost(getId(), val, getProblem()) > cost && checkSelfAssignment(val).equals("")) {
						bestVal = val;
						cost = cpa.calcAddedCost(getId(), val, getProblem());
					}
				}
				assignNewValue(bestVal);
			}
			else {
				currentDomain = noValidDomain();
				while(!currentDomain.isEmpty()) {
					int val = currentDomain.iterator().next();
					currentDomain.remove(val);
					if (cpa.calcAddedCost(getId(), val, getProblem()) > cost) {
						bestVal = val;
						cost = cpa.calcAddedCost(getId(), val, getProblem());
					}
				}
				assignNewValue(bestVal);
			}
		}

		else if(!flag && rnd.nextDouble() < p1) {
			int cost = cpa.calcAddedCost(getId(), getSubmitedCurrentAssignment(), getProblem());
			while(!currentDomain.isEmpty()) {
				int val = currentDomain.iterator().next();
				currentDomain.remove(val);
				if (cpa.calcAddedCost(getId(), val, getProblem()) > cost && checkSelfAssignment(val).equals("")) {	
					bestVal = val;
					cost = cpa.calcAddedCost(getId(), val, getProblem());
				}
			}
			assignNewValue(bestVal);
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

	private void assignNewValue(int val) {
		int assign = -1;
		try {
			assign = getSubmitedCurrentAssignment();
		}
		catch(Exception e) {}
		if(val != assign) {
			cpa.assign(getId(), val);
			submitCurrentAssignment(val);
			broadcast("ASSIGNMENT", getId(), val);
		}
	}
}
