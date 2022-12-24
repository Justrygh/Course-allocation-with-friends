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

@Algorithm(name="HBS", useIdleDetector=false)
public class HBS extends SimpleAgent {

	@Variable(name="it", defaultValue="5", description="number of iterations to perform")
	private int it = 5;

	@Variable(name="courseLimit", defaultValue="10", description="number of max. students per course")
	private int courseLimit = 30;

	private Assignment cpa;
	public static int rounds;

	private HashMap<String, Integer> coursesAssignments; 
	private static Builder builder;

	private void iterate() {
		coursesAssignments = builder.mapAssignments(cpa);
		assignNewValue(builder.find_max_value(getDomain(), coursesAssignments, cpa, getId()));
	}

	@Override
	public void start() {
		if(builder == null) {
			builder = new Builder(getProblem(), courseLimit);
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
				builder.setExtraCourses(builder.calculate_extra_courses(builder.mapAssignments(cpa)));
				builder.output("HBS", cpa);
				builder = null;
				rounds = 0;
			}
		}
		
	}

	private synchronized void assignNewValue(int val) {
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
