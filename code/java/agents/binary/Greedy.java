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

@Algorithm(name="Greedy", useIdleDetector=false)
public class Greedy extends SimpleAgent {

	@Variable(name="courseLimit", defaultValue="10", description="number of max. students per course")
	private int courseLimit = 30;
	
	private Assignment cpa;
	private static Builder builder;
	
			
	@Override
	public void start() {
		if(builder == null) {
			builder = new Builder(getProblem(), courseLimit);
		}
		
		cpa = new Assignment();
		assignNewValue(find_max_value());
	}
	
	private int find_max_value() {
		Set<Integer> currentDomain = new HashSet<Integer>(getDomain());
		int bestVal = 0;
		int cost = -1;
		while(!currentDomain.isEmpty()) {
			int val = currentDomain.iterator().next();
			currentDomain.remove(val);
			if (getProblem().getConstraintCost(getId(), val) > cost) {	
				bestVal = val;
				cost = getProblem().getConstraintCost(getId(), val);
			}
		}
		return bestVal;
	}
	
	@WhenReceived("ASSIGNMENT")
	public void handleAssignment(int i, int v) {
		cpa.assign(i, v);
	}
	
	@Override
	public void onMailBoxEmpty() {
		finish(getSubmitedCurrentAssignment());
		builder.update();
		if(builder.getAgents() == getNumberOfVariables()) {
			builder.setExtraCourses(builder.calculate_extra_courses(builder.mapAssignments(cpa)));
			builder.output("Greedy", cpa);
			builder = null;
		}
		return;
	}
	
	private void assignNewValue(int val) {
		cpa.assign(getId(), val);
		submitCurrentAssignment(val);	
		broadcast("ASSIGNMENT", getId(), val);
	}
}
