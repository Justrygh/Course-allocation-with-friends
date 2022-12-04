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

import bgu.dcr.az.api.*;
import bgu.dcr.az.api.agt.*;
import bgu.dcr.az.api.ano.*;
import bgu.dcr.az.api.tools.*;
import ext.sim.agents.Builder.*;

@Algorithm(name="DSA_RCUnary", useIdleDetector=false)
public class DSA_RCUnary extends SimpleAgent {

	@Variable(name="it", defaultValue="500", description="number of iterations to perform")
	private int it = 500;

	@Variable(name="p1", defaultValue="0.8", description="probability of value change if assignment is valid")
	private double alpha = 0.8;

	@Variable(name="courseLimit", defaultValue="10", description="number of max. students per course")
	private int courseLimit = 30;

	private Assignment bestCpa;
	private Assignment cpa;
	
	private static int[] avg_utilities;
	private static int[] avg_iterations;

	private HashMap<String, Integer> coursesAssignments; 
	private static Builder builder;

	@Override
	public void start() {
		if(builder == null) {
			builder = new Builder(getProblem(), courseLimit);
			avg_utilities = new int[3];
			avg_iterations = new int[3];
		}
		
		cpa = new Assignment();
		int rand = builder.chooseAtRandom(getDomainSize());
		assignNewValue(rand);
	}


	@WhenReceived("ASSIGNMENT") 
	public void handleAssignment(int i, int v) {
		cpa.assign(i, v);
	}


	/** ========================= Max - Beta ========================= */

	private Set<Integer> validDomain(String key){
		Set<Integer> currentDomain = new HashSet<Integer>(getDomain());
		Set<Integer> newDomain = new HashSet<Integer>(getDomain());
		for(int value: currentDomain) { 
			for(String str: builder.getCourseCombinations().get(value)) {
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
			for(String str: builder.getCourseCombinations().get(value)) {
				if(validCourses.contains(str))
					courses_contained += 1;
			}
			if(courses_contained != validCourses.size())
				newDomain.remove(value);
		}
		return newDomain;
	}

	/** ========================= Max - Beta ========================= */

	@Override
	public void onMailBoxEmpty() {
		if (getSystemTimeInTicks() >= it) {
			builder.update();
			finish(getSubmitedCurrentAssignment());
			if(builder.getAgents() == getNumberOfVariables()) {
//				builder.setExtraCourses(builder.calculate_extra_courses(builder.mapAssignments(cpa))); // Uncomment this if any-time algorithm is down
				builder.outputUnary("DSA_RC_Unary", bestCpa);
				builder = null;
				reset();
			}
			return;
		}

		String beta_key = "";
		double beta = 0;

		boolean flag = false;
		int bestVal = 0;

		coursesAssignments = builder.mapAssignments(cpa);

		try {
			int assign = getSubmitedCurrentAssignment();
			beta_key = builder.checkSelfAssignment(assign, coursesAssignments); 
		}
		catch(Exception e) {}

		/** Caluculate beta */
		if(!beta_key.equals("")) {
			flag = true;
			beta = (double) (this.coursesAssignments.get(beta_key)-this.courseLimit) / this.coursesAssignments.get(beta_key); 
		}
		
		/** Anytime */
		int calc_course = builder.calculate_extra_courses(coursesAssignments);
		if(bestCpa == null || calc_course < builder.getExtraCourses() || (calc_course ==  builder.getExtraCourses() && cpa.calcCost(getProblem()) > bestCpa.calcCost(getProblem()))) { 
			bestCpa = cpa.deepCopy();
			builder.setExtraCourses(calc_course);
		}

		if(flag && builder.getRandom() < beta) {
			int cost = -1;
			int values = 0;
			for(int val: coursesAssignments.values()) {
				if(val < courseLimit)
					values += 1;
			}
			if(values >= builder.getCourseCombinations().get(0).length) {
				assignNewValue(builder.find_max_value_unary(validDomain(beta_key), coursesAssignments, cpa, getId()));
			}
			else {
				Set<Integer> currentDomain = noValidDomain();
				while(!currentDomain.isEmpty()) {
					int val = currentDomain.iterator().next();
					currentDomain.remove(val);
					if (cpa.calcAddedCost(getId(), val, getProblem()) >= cost) {
						bestVal = val;
						cost = cpa.calcAddedCost(getId(), val, getProblem());
//						builder.setExtraCourses(builder.calculate_extra_courses(coursesAssignments)); 
					}
				}
				assignNewValue(bestVal);
			}
			
		}
		else if(!flag && builder.getRandom() < alpha) {
			assignNewValue(builder.find_max_value_unary(getDomain(), coursesAssignments, cpa, getId()));
		}
	}
	
	private void reset() {
		avg_iterations = null;
		avg_utilities = null;
	}
	
	private void assignNewValue(int val) {
		cpa.assign(getId(), val);
		submitCurrentAssignment(val);
		broadcast("ASSIGNMENT", getId(), val);
	}
}
