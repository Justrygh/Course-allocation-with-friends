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

@Algorithm(name="IterativeUnary", useIdleDetector=false)
public class IterativeUnary extends SimpleAgent {

	@Variable(name="courseLimit", defaultValue="10", description="number of max. students per course")
	private int courseLimit = 30;
	
	private Assignment cpa;
	private static boolean last_done;

	private HashMap<String, Integer> coursesAssignments; 
	private static Builder builder;
	
	private synchronized void iterate() {
		coursesAssignments = builder.mapAssignments(cpa);
		assignNewValue(builder.find_max_value_unary(getDomain(), coursesAssignments, cpa, getId()));
	}

	@Override
	public void start() {
		if(builder == null) {
			builder = new Builder(getProblem(), courseLimit);
			last_done = false;
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
	
	public void onMailBoxEmpty() {
		if(last_done) {
			if(getId() == builder.getAgents()) {
				finish(cpa.getAssignment(getId()));
				builder.update();
				if(isLastAgent() && builder.getAgents() == getProblem().getNumberOfVariables()) {
					builder.setExtraCourses(builder.calculate_extra_courses(builder.mapAssignments(cpa)));
					builder.outputUnary("Iterative_Unary", cpa);
					builder = null;
				}
			}
		}
	}
	
	private synchronized void assignNewValue(int val) {
		cpa.assign(getId(), val);
		submitCurrentAssignment(val);	
		broadcast("ASSIGNMENT", getId(), val);
		if(getId() != getProblem().getNumberOfVariables()-1)
			send("START").toNextAgent();
		else
			last_done = true;
	}
}
