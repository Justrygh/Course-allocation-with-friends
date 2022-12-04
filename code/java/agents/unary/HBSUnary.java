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
import java.util.Set;

import bgu.dcr.az.api.*;
import bgu.dcr.az.api.agt.*;
import bgu.dcr.az.api.ano.*;
import bgu.dcr.az.api.tools.*;

@Algorithm(name="HBSUnary", useIdleDetector=false)
public class HBSUnary extends SimpleAgent {

	@Variable(name="it", defaultValue="5", description="number of iterations to perform")
	private int it = 5;

	@Variable(name="courseLimit", defaultValue="10", description="number of max. students per course")
	private int courseLimit = 30;

	private Assignment cpa;
	public static int rounds;
	
	private static int[] avg_utilities;
	private static int[] avg_iterations;

	private HashMap<String, Integer> coursesAssignments; 
	private static Builder builder;

	private void iterate() {
		coursesAssignments = builder.mapAssignments(cpa);
		//System.out.println("Agent: " + Integer.toString(getId()) + " , Ilegal Assignments: " + Integer.toString(builder.calculate_extra_courses(coursesAssignments)));
		assignNewValue(builder.find_max_value_unary(getDomain(), coursesAssignments, cpa, getId()));
	}

	@Override
	public void start() {
		if(builder == null) {
			builder = new Builder(getProblem(), courseLimit);
			avg_utilities = new int[3];
			avg_iterations = new int[3];
		}
		
		cpa = new Assignment();
		if(isFirstAgent()) {
			iterate();
		}
	}

	@WhenReceived("ASSIGNMENT")
	public void handleAssignment(int i, int v) {
		cpa.assign(i, v);
	}

	@WhenReceived("START")
	public void handleStart() {
		iterate();
	}

	@Override
	public void onMailBoxEmpty() {
		if (rounds >= it) {
			finish(getSubmitedCurrentAssignment());
			builder.update();
			if(builder.getAgents() == getNumberOfVariables()) {
				builder.setExtraCourses(builder.calculate_extra_courses(builder.mapAssignments(cpa))); // _iter_" + Integer.toString(it) + ".csv"
				builder.outputUnary("HBS_Unary", cpa);
				builder = null;
				rounds = 0;
				reset();
			}
		}
		
	}
	
	private void reset() {
		avg_iterations = null;
		avg_utilities = null;
	}
	
	private void assignUtility(int val) {
		if(getId() == 0) {
			avg_utilities[0] += (cpa.calcCost(getProblem()) - cpa.calcCostWithout(getId(), getProblem()));
			avg_iterations[0] += 1;
		}
		else if(getId() == (getProblem().getNumberOfVariables()+1)/2) {
			avg_utilities[1] += (cpa.calcCost(getProblem()) - cpa.calcCostWithout(getId(), getProblem()));
			avg_iterations[1] += 1;
		}
		else if(getId() == getProblem().getNumberOfVariables()-1) {
			avg_utilities[2] += (cpa.calcCost(getProblem()) - cpa.calcCostWithout(getId(), getProblem()));
			avg_iterations[2] += 1;
		}
	}

	private synchronized void assignNewValue(int val) {
		assignUtility(val);
		cpa.assign(getId(), val);
		submitCurrentAssignment(val);	
		broadcast("ASSIGNMENT", getId(), val);
		if(rounds % 2 == 0) {
			if(!isLastAgent())
				send("START").toNextAgent();
			else {
				rounds++;
				if(rounds < it)
					iterate();
			}
		}
		else {
			if(!isFirstAgent())
				send("START").toPreviousAgent();
			else {
				rounds++;
				if(rounds < it)
					iterate();
			}
		}
	}
}
