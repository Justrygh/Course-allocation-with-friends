package ext.sim.agents;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import bgu.dcr.az.api.*;
import bgu.dcr.az.api.agt.*;
import bgu.dcr.az.api.ano.*;
import bgu.dcr.az.api.tools.*;

@Algorithm(name="Random", useIdleDetector=false)
public class Random extends SimpleAgent {

	@Variable(name="courseLimit", defaultValue="10", description="number of max. students per course")
	private int courseLimit = 30;
	
	private Assignment cpa;
	public static int rounds;

	private static Builder builder;
			
	@Override
	public void start() {
		if(builder == null)
			builder = new Builder(getProblem(), courseLimit);
		
		cpa = new Assignment();
		int rand = builder.chooseAtRandom(getDomainSize());
		assignNewValue(rand);
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
			builder.output("Random", cpa);
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




