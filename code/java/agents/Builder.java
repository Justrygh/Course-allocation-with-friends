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
import bgu.dcr.az.api.prob.*;
import bgu.dcr.az.api.tools.*;

public class Builder {

	private int numCourses = 3;
	private int numAgents = 177;

	/** Assignment variables */
	private ImmutableProblem p;
	private int courseLimit;

	private Random rnd = new Random();

	private int[][] coursesRating = null; 
	private int[][] friendsRating = null; 
	private List<String[]> courseCombinations = readCombinations("combinations.csv");

	private static int num_agents;
	private static int extra_courses;
	private static boolean done;

	private static final Object counterLock = new Object();
	private static final LocalTime start_time = LocalTime.now();

	public Builder(ImmutableProblem problem, int limit) {
		p = problem;
		courseLimit = limit;

		done = false;
		num_agents = 0;
		extra_courses = Integer.MAX_VALUE;

		coursesRating = readCourses("courses_swap.csv");
		friendsRating = readFriendship("friendship_swap.csv");
	}

	public int chooseAtRandom(int limit) {
		return rnd.nextInt(limit);
	}

	public void update() {
		synchronized(counterLock) {
			num_agents++;
		}
	}

	public void clear() {
		num_agents = 0;
		extra_courses = 0;
		done = false;
	}

	public double gini_coef(Assignment cpa) {
		double coef = 0;
		double mean = 0;
		for(int i=0; i<p.getNumberOfVariables(); i++) {
			mean += cpa.calcAddedCost(i, cpa.getAssignment(i), p);
		}
		mean /= p.getNumberOfVariables();

		for(int i=0; i<p.getNumberOfVariables(); i++) {
			for(int j=0; j<p.getNumberOfVariables(); j++) {
				coef += Math.abs(cpa.calcAddedCost(i, cpa.getAssignment(i), p) - cpa.calcAddedCost(j, cpa.getAssignment(j), p));
			}
		}
		coef /= 2*Math.pow(p.getNumberOfVariables(), 2)*mean;
		return coef*100;
	}

	public double gini_coef_unary(Assignment cpa) {
		double coef = 0;
		double mean = 0;
		for(int i=0; i<p.getNumberOfVariables(); i++) {
			mean += p.getConstraintCost(i, cpa.getAssignment(i));
		}
		mean /= p.getNumberOfVariables();

		for(int i=0; i<p.getNumberOfVariables(); i++) {
			for(int j=0; j<p.getNumberOfVariables(); j++) {
				coef += Math.abs(p.getConstraintCost(i, cpa.getAssignment(i)) - p.getConstraintCost(j, cpa.getAssignment(j)));
			}
		}
		coef /= 2*Math.pow(p.getNumberOfVariables(), 2)*mean;
		return coef*100;
	}

	public int calculate_extra_courses(HashMap<String, Integer> coursesAssignments) {
		int extra_courses = 0;
		for(String key: coursesAssignments.keySet()) {
			if(coursesAssignments.get(key) > courseLimit) {
				extra_courses += (coursesAssignments.get(key) - courseLimit);
			}
		}
		return extra_courses;
	}

	public HashMap<String, Integer> mapAssignments(Assignment cpa) {
		HashMap<String, Integer> coursesAssignments = new HashMap<>();
		for(int var: cpa.assignedVariables()) {
			for(String str: courseCombinations.get(cpa.getAssignment(var))) {
				if(coursesAssignments.get(str) == null) 
					coursesAssignments.put(str, 1);
				else 
					coursesAssignments.put(str, coursesAssignments.get(str)+1);
			}
		}
		return coursesAssignments;
	}

	public String checkSelfAssignment(int val, HashMap<String, Integer> coursesAssignments) {
		int max_beta = 0;
		String course_key = "";
		for(String str: courseCombinations.get(val)) { 
			if(coursesAssignments.get(str) != null && coursesAssignments.get(str) > max_beta && coursesAssignments.get(str) > courseLimit) {
				max_beta = coursesAssignments.get(str);
				course_key = str;
			}

		}
		return course_key;
	}

	public String checkNewAssignment(int old_val, int new_val, HashMap<String, Integer> coursesAssignments) {
		int max_beta = courseLimit;
		String course_key = "";
		if(old_val != -1) {
			for(String str: courseCombinations.get(old_val)) {
				coursesAssignments.put(str, coursesAssignments.get(str)-1);
			}
		}
		for(String str: courseCombinations.get(new_val)) { 
			if(coursesAssignments.get(str) != null && coursesAssignments.get(str) >= max_beta) {
				max_beta = coursesAssignments.get(str);
				course_key = str;
			}

		}
		return course_key;
	}

	public int intersection_courses(String[] courses1, String[] courses2) {
		int common = 0;
		for(String str1: courses1) {
			for(String str2: courses2) {
				if(str1.equals(str2))
					common++;
			}
		}
		return common;
	}

	public int calc_friends(Assignment cpa) {
		int friends = 0;
		for(int i: cpa.assignedVariables()) {
			for(int j: p.getNeighbors(i)) {
				friends += intersection_courses(courseCombinations.get(cpa.getAssignment(i)), courseCombinations.get(cpa.getAssignment(j)));
			}
		}
		return friends;
	}
	
	private void printAssignments(Assignment cpa) {
		for(int i: cpa.assignedVariables()) {
			System.out.println("ID: " + Integer.toString(i) + ", Assignment: " + String.join(" & ", courseCombinations.get(cpa.getAssignment(i))) + ", Cost: " + Integer.toString(calculateAgentCost(cpa, i, cpa.getAssignment(i))));
		}
	}
	
	private int calculateTotalCost(Assignment cpa) {
		int cost=0;
		for(int i: cpa.assignedVariables()) {
			cost += calculateAgentCost(cpa, i, cpa.getAssignment(i));
		}
		return cost;
	}

	public void output(String experiment, Assignment cpa) {
		String filename = ".\\"+experiment+"_Agent\\" + Integer.toString(p.getNumberOfVariables()) + "agents.csv";
//		String filename = ".\\"+experiment+"_Agent\\" + Integer.toString(courseLimit) + "courseLimit.csv";
		synchronized(counterLock) {
			if(getAgents() == p.getNumberOfVariables() && !done) {
				done = true;
				try {
					BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true));
					writer.write(String.join(",", Integer.toString(calculateTotalCost(cpa)), Integer.toString(getExtraCourses()), Double.toString(gini_coef(cpa)), Integer.toString(calc_friends(cpa)), to_String(cpa)));
					writer.newLine();
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				clear();
			}
		}
	}
	
	private String to_String(Assignment cpa) {
		int mid_agent = cpa.getNumberOfAssignedVariables()/2;
		int last_agent = cpa.getNumberOfAssignedVariables() - 1;
		return Integer.toString(calculateAgentCost(cpa, 0, cpa.getAssignment(0))) + "," + Integer.toString(calculateAgentCost(cpa, mid_agent, cpa.getAssignment(mid_agent))) + "," + Integer.toString(calculateAgentCost(cpa, last_agent, cpa.getAssignment(last_agent)));
	}
	
	private String to_String_Unary(Assignment cpa) {
		int mid_agent = cpa.getNumberOfAssignedVariables()/2;
		int last_agent = cpa.getNumberOfAssignedVariables() - 1;
		return Integer.toString(calculateUnary(0, convertDomain2Courses(cpa.getAssignment(0)))) + "," + Integer.toString(calculateUnary(mid_agent, convertDomain2Courses(cpa.getAssignment(mid_agent)))) + "," + Integer.toString(calculateUnary(last_agent, convertDomain2Courses(cpa.getAssignment(last_agent))));
	}
	
	private int calculateTotalCostUnary(Assignment cpa) {
		int cost=0;
		for(int i: cpa.assignedVariables()) {
			cost += calculateUnary(i, convertDomain2Courses(cpa.getAssignment(i)));
		}
		return cost;
	}

	public void outputUnary(String experiment, Assignment cpa) {
		String filename = ".\\"+experiment+"_Agent\\" + Integer.toString(p.getNumberOfVariables()) + "agents.csv";
// 		String filename = ".\\"+experiment+"_Agent\\" + Integer.toString(courseLimit) + "courseLimit.csv";
		synchronized(counterLock) {
			if(getAgents() == p.getNumberOfVariables() && !done) {
				done = true;
				try {
					BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true));
					writer.write(String.join(",", Integer.toString(calculateTotalCostUnary(cpa)), Integer.toString(getExtraCourses()), Double.toString(gini_coef(cpa)), Integer.toString(calc_friends(cpa)), to_String_Unary(cpa)));
					writer.newLine();
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				clear();
			}
		}
	}

	public int checkMinAssignment(int new_val, HashMap<String, Integer> coursesAssignments) {
		int exceed = 0;
		for(String str: courseCombinations.get(new_val)) { 
			if(coursesAssignments.get(str) != null && coursesAssignments.get(str) >= courseLimit) {
				exceed += (coursesAssignments.get(str) - courseLimit);
			}
		}
		return exceed;
	}

	private int[] convertDomain2Courses(int domain) {
		int[] courses = new int[numCourses];
		int index = 0;
		for(String str: courseCombinations.get(domain)) {
			int course_number = Character.getNumericValue(str.charAt(1))-1;
			courses[index] = course_number;
			index++;
		}
		return courses;
	}

	private int calculateUnary(int agent, int[] courses) {
		int price = 0;
		for(int val: courses) {
			price += coursesRating[agent][val];
		}
		return price;
	}
	
	private int calculateBinary(Assignment cpa, int agent, int[] courses) {
		int price = 0;
		for(int i=0; i<friendsRating[agent].length; i++) {
			if(friendsRating[agent][i] > 0) {
				try {
					price += intersectionSize(courses, convertDomain2Courses(cpa.getAssignment(i))) * friendsRating[agent][i];
				}
				catch(Exception e) {}
			}
		}
		return price;
	}
	
	public int calculateAgentCost(Assignment cpa, int agent, int domain) {
		int[] courses = convertDomain2Courses(domain);
		return calculateUnary(agent, courses) + calculateBinary(cpa, agent, courses);
	}

	public int find_max_value(Set<Integer> domain, HashMap<String, Integer> coursesAssignments, Assignment cpa, int id) {
		Set<Integer> currentDomain = new HashSet<Integer>(domain);
		int bestVal = -1;
		int cost = -1;
		int oldVal = -1;
		try {
			oldVal = cpa.getAssignment(id);
		}
		catch(Exception e) {}
		while(!currentDomain.isEmpty()) {
			int val = currentDomain.iterator().next();
			currentDomain.remove(val);
			if (calculateAgentCost(cpa, id, val) > cost && checkNewAssignment(oldVal, val, coursesAssignments).equals("")) {	
				bestVal = val;
				cost = calculateAgentCost(cpa, id, val);
			}
		}
		if(bestVal == -1) {
			currentDomain = new HashSet<Integer>(domain);
			int exceed = Integer.MAX_VALUE;
			while(!currentDomain.isEmpty()) {
				int val = currentDomain.iterator().next();
				currentDomain.remove(val);
				int min = checkMinAssignment(val, coursesAssignments);
				if ((calculateAgentCost(cpa, id, val) >= cost && min <= exceed) || min < exceed) {	
					bestVal = val;
					cost = calculateAgentCost(cpa, id, val);
					exceed = min;
				}
			}
		}
		//insert_cost(id, cost);
		return bestVal;
	}

	public int find_max_value_unary(Set<Integer> domain, HashMap<String, Integer> coursesAssignments, Assignment cpa, int id) {
		Set<Integer> currentDomain = new HashSet<Integer>(domain);
		int bestVal = -1;
		int cost = -1;
		int oldVal = -1;
		try {
			oldVal = cpa.getAssignment(id);
		}
		catch(Exception e) {
			oldVal = -1;
		}
		while(!currentDomain.isEmpty()) {
			int val = currentDomain.iterator().next();
			currentDomain.remove(val);
			if (calculateUnary(id, convertDomain2Courses(val)) > cost && checkNewAssignment(oldVal, val, coursesAssignments).equals("")) {	
				bestVal = val;
				cost = calculateUnary(id, convertDomain2Courses(val));
			}
		}
		if(bestVal == -1) {
			currentDomain = new HashSet<Integer>(domain);
			int exceed = Integer.MAX_VALUE;
			while(!currentDomain.isEmpty()) {
				int val = currentDomain.iterator().next();
				currentDomain.remove(val);
				int min = checkMinAssignment(val, coursesAssignments);
				if ((calculateUnary(id, convertDomain2Courses(val)) >= cost && min <= exceed) || min < exceed) {	
					bestVal = val;
					cost = calculateUnary(id, convertDomain2Courses(val));
					exceed = min;
				}
			}
		}
		//insert_cost(id, cost);
		return bestVal;
	}


	public List<String[]> readCombinations(String csv) {
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
	
	private int intersectionSize(int[] arr1, int[] arr2) {
		Set<Integer> set = new HashSet<Integer>();
		for(int i=0; i<arr1.length; i++) {
			for(int j=0; j<arr2.length; j++) {
				if(arr1[i] == arr2[j]) {
					set.add(arr1[i]);
				}
			}
		}
		return set.size();
	}

	private int[][] readCourses(String csv) {
		int[][] coursesRatings = null;
		String line = "";  
		String splitBy = ",";  
		Boolean first = false;
		int index = 0;
		int c_len = 0;
		try   
		{  
			BufferedReader br = new BufferedReader(new FileReader(csv));  
			while ((line = br.readLine()) != null && index < p.getNumberOfVariables()) 
			{  
				String[] ratings = line.split(splitBy); // 9,2,3,5,8,7,1,4,6
				if(!first) {
					c_len = ratings.length;
					coursesRatings = new int[numAgents][c_len];
					first = true;
				}
				else {
					for(int i=0; i<ratings.length; i++) {
						coursesRatings[index][i] = Integer.parseInt(ratings[i]);
					}
					index++;
				}
			}
			br.close();
		}   
		catch (IOException e)   
		{  
			e.printStackTrace();  
		}
		return coursesRatings;
	}
	
	private int[][] readFriendship(String csv) {
		String line = "";  
		String splitBy = ",";  
		Boolean first = false;
		int index = 0;
		int n = p.getNumberOfVariables();
		int[][] friendsRating = null;
		try   
		{  
			BufferedReader br = new BufferedReader(new FileReader(csv));  
			while ((line = br.readLine()) != null) 
			{  
				String[] ratings = line.split(splitBy); 
				if(!first) {
					//this.n = ratings.length;
					friendsRating = new int[n][n];
					first = true;
				}
				else {
					for(int i=0; i<ratings.length; i++) {
						friendsRating[index][i] = Integer.parseInt(ratings[i]);
					}
					index++;
				}
			}
			br.close();
		}   
		catch (IOException e)   
		{  
			e.printStackTrace();  
		}
		return friendsRating;
	}


	/**
	 * Getters & Setters
	 */

	public int getAgents() {
		return num_agents;
	}

	public List<String[]> getCourseCombinations(){
		return courseCombinations;
	}

	public LocalTime getTime() {
		return start_time;
	}

	public int getExtraCourses() {
		return extra_courses;
	}

	public double getRandom() {
		return rnd.nextDouble();
	}

	public void setExtraCourses(int courses) {
		extra_courses = courses;
	}

}
