package ext.sim.agents;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.google.common.collect.Lists;

import bgu.dcr.az.api.agt.*;
import bgu.dcr.az.api.ano.*;
import bgu.dcr.az.api.prob.*;
import bgu.dcr.az.api.tools.*;

public class Builder {

	private HashMap<String, Integer> problemParams = new HashMap<>();

	/** Assignment variables */
	private ImmutableProblem p;
	private int courseLimit;

	private Random rnd = new Random();

	private int[][] coursesRating = null; 
	private int[][] friendsRating = null; 
	private List<String[]> courseCombinations = readCombinations("combinations.csv");

	private static int num_agents;
	private static int extra_courses;
	private static int num_rounds = 0;
	private static boolean done;
	
	private List<String> filesCopy;

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
		problemParams = readParams("Parameters.txt");
		filesCopy = new ArrayList<>(Lists.newArrayList("courses_swap", "friendship_swap"));
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

	private double gini_coef(Assignment cpa) {
		double coef = 0;
		double mean = 0;
		mean = (double) calculateTotalCost(cpa) / cpa.getNumberOfAssignedVariables();

		for(int i=0; i<cpa.getNumberOfAssignedVariables(); i++) {
			for(int j=0; j<cpa.getNumberOfAssignedVariables(); j++) {
				coef += Math.abs(calculateAgentCost(cpa, i, cpa.getAssignment(i)) - calculateAgentCost(cpa, j, cpa.getAssignment(j)));
			}
		}
		coef /= 2*Math.pow(cpa.getNumberOfAssignedVariables(), 2)*mean;
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

	private String checkNewAssignment(int old_val, int new_val, HashMap<String, Integer> coursesAssignments) {
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

	private int intersection_courses(String[] courses1, String[] courses2) {
		int common = 0;
		for(String str1: courses1) {
			for(String str2: courses2) {
				if(str1.equals(str2))
					common++;
			}
		}
		return common;
	}

	private int calc_friends(Assignment cpa) {
		int friends = 0;
		for(int i: cpa.assignedVariables()) {
			friends += find_friends(cpa, i);
		}
		return friends;
	}
	
	private int find_friends(Assignment cpa, int id) {
		int friends = 0;
		for(int i: p.getNeighbors(id)) {
			friends += intersection_courses(courseCombinations.get(cpa.getAssignment(id)), courseCombinations.get(cpa.getAssignment(i)));
		}
		return friends;
	}
	
	private String friends_toString(Set<Integer> friends) {
		String output = "";
		for(int i: friends) 
			output += "a" + Integer.toString(i) + " & ";
		if(output.length() > 0)
			output.substring(0, output.length()-3);
		return output;
	}
	
	private String courses_toString(Assignment cpa, int id) {
		String output = "";
		for(String course1: courseCombinations.get(cpa.getAssignment(id))) {
			output += course1 + ": ";
			for(int friend: p.getNeighbors(id)) {
				for(String course2: courseCombinations.get(cpa.getAssignment(friend))) {
					if(course1 == course2)
						output += "a" + Integer.toString(friend) + " & ";
				}
			}
			if(output.charAt(output.length()-2) == '&') 
				output = output.substring(0, output.length()-2);
			output += "| ";
		}
		return output.substring(0, output.length()-3);
	}
	
	private void saveAssignments(Assignment cpa, String filename) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true));
			writer.write(String.join(",", "ID", "Assignment", "Total Utiltiy", "Courses Utility", "Friendship Utility", "Number of Friends in Courses", "List of Friends", "Friends Assignments"));
			writer.newLine();
			for(int i: cpa.assignedVariables()) {
				writer.write(String.join(",", Integer.toString(i), String.join(" & ", courseCombinations.get(cpa.getAssignment(i))), Integer.toString(calculateAgentCost(cpa, i, cpa.getAssignment(i))), Integer.toString(calculateUnary(i, convertDomain2Courses(cpa.getAssignment(i)))), Integer.toString(calculateBinary(cpa, i, convertDomain2Courses(cpa.getAssignment(i)))), Integer.toString(find_friends(cpa, i)), friends_toString(p.getNeighbors(i)), courses_toString(cpa, i)));
				writer.newLine();
			}
			writer.newLine();
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private int calculateTotalCost(Assignment cpa) {
		int cost=0;
		for(int i: cpa.assignedVariables()) {
			cost += calculateAgentCost(cpa, i, cpa.getAssignment(i));
		}
		return cost;
	}
	
	private int calculateTotalUnary(Assignment cpa) {
		int cost=0;
		for(int i: cpa.assignedVariables()) {
			cost += calculateUnary(i, convertDomain2Courses(cpa.getAssignment(i)));
		}
		return cost;
	}
	
	private int calculateTotalBinary(Assignment cpa) {
		int cost=0;
		for(int i: cpa.assignedVariables()) {
			cost += calculateBinary(cpa, i, convertDomain2Courses(cpa.getAssignment(i)));
		}
		return cost;
	}
	
	private static void copyFile(File src, File dest) throws IOException {
	    Files.copy(src.toPath(), dest.toPath());
	} 

	public void output(String experiment, Assignment cpa) {
		String basepath = experiment + "_Agent\\";
		String filename = basepath;
		if(problemParams.get("problemDefinition") == 0) {
			filename += Integer.toString(p.getNumberOfVariables()) + "agents_";
			basepath = "Debug\\" + basepath + "Agents\\x" + Integer.toString(problemParams.get("weight")) + "\\" + Integer.toString(p.getNumberOfVariables());
		}
		else {
			filename += Integer.toString(courseLimit) + "courseLimit_";
			basepath = "Debug\\" + basepath + "CourseLimit\\x" + Integer.toString(problemParams.get("weight")) + "\\" + Integer.toString(courseLimit);
		}
		filename += Integer.toString(problemParams.get("weight")) + "weight.csv";
		synchronized(counterLock) {
			if(getAgents() == p.getNumberOfVariables() && !done) {
				done = true;
				num_rounds += 1;
				try {
					BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true));
					writer.write(String.join(",", Integer.toString(calculateTotalCost(cpa)), Integer.toString(getExtraCourses()), Double.toString(gini_coef(cpa)), Integer.toString(calc_friends(cpa)), to_String(cpa), Integer.toString(calculateTotalUnary(cpa)), Integer.toString(calculateTotalBinary(cpa))));
					writer.newLine();
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				if(problemParams.get("debug") == 1) {
					new File(basepath).mkdirs();
					for(String file: filesCopy) {
						try {
							copyFile(new File(file + ".csv"), new File(basepath + "\\" + file + "_" + Integer.toString(((num_rounds / 6) % 50) + 1) + ".csv"));
							// TODO: Think out to save the filename when performing multiple experiments
						} catch (IOException e) {}
					}
					saveAssignments(cpa, basepath + "\\log_" + Integer.toString(((num_rounds / 6) % 50) + 1) + ".csv");
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

	private int checkMinAssignment(int new_val, HashMap<String, Integer> coursesAssignments) {
		int exceed = 0;
		for(String str: courseCombinations.get(new_val)) { 
			if(coursesAssignments.get(str) != null && coursesAssignments.get(str) >= courseLimit) {
				exceed += (coursesAssignments.get(str) - courseLimit);
			}
		}
		return exceed;
	}

	private int[] convertDomain2Courses(int domain) {
		int[] courses = new int[problemParams.get("numCourses")];
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
					coursesRatings = new int[p.getNumberOfVariables()][c_len];
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

	private HashMap<String, Integer> readParams(String text) {
		String line = "";
		String splitBy = ",";
		String keyValue = "=";
		HashMap<String, Integer> problemParamsMap = new HashMap<>();
		try
		{
			BufferedReader br = new BufferedReader(new FileReader(text));
			String[] params = br.readLine().split(splitBy);
			for(String arg: params){
				String[] values = arg.split(keyValue);
				problemParamsMap.put(values[0], Integer.parseInt(values[1]));
			}
			br.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return problemParamsMap;
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
