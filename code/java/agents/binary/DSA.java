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

@Algorithm(name="DSA", useIdleDetector=false)
public class DSA extends SimpleAgent {

	@Variable(name="it", defaultValue="500", description="number of iterations to perform")
	private int it = 500;

	@Variable(name="p", defaultValue="0.8", description="probability of value change if assignment is valid")
	private double p = 0.8;

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

	@Override
	public void onMailBoxEmpty() {
		if (getSystemTimeInTicks() >= it) {
			finish(getSubmitedCurrentAssignment());
			builder.update();
			if(builder.getAgents() == getNumberOfVariables()) {
//				builder.setExtraCourses(builder.calculate_extra_courses(builder.mapAssignments(cpa))); // Uncomment this if any-time algorithm is down
				builder.output("DSA", bestCpa);
				builder = null;
				reset();
			}
			return;
		}

		coursesAssignments = builder.mapAssignments(cpa);
		int calc_course = builder.calculate_extra_courses(coursesAssignments);
		if(bestCpa == null || calc_course < builder.getExtraCourses() || (calc_course ==  builder.getExtraCourses() &&  cpa.calcCost(getProblem()) > bestCpa.calcCost(getProblem()))) { 
			bestCpa = cpa.deepCopy();
			builder.setExtraCourses(calc_course);
		}

		Set<Integer> currentDomain = new HashSet<Integer>(getDomain());
		currentDomain.remove(getSubmitedCurrentAssignment());

		if(builder.getRandom() < p) {
			assignNewValue(builder.find_max_value(getDomain(), coursesAssignments, cpa, getId()));
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




