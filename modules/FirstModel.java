package ext.sim.modules;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import bgu.dcr.az.api.ano.Register;
import bgu.dcr.az.api.ano.Variable;
import bgu.dcr.az.api.prob.Problem;
import bgu.dcr.az.api.prob.ProblemType;
import bgu.dcr.az.exen.pgen.AbstractProblemGenerator;

@Register(name = "FirstModel")
public class FirstModel extends AbstractProblemGenerator {

	@Variable(name = "n", description = "number of variables", defaultValue = "30")
	int n = 30;
	
	@Variable(name = "c", description = "number of courses desired", defaultValue = "5")
	int c = 2;
	
	@Variable(name = "s", description = "number of students", defaultValue = "146")
	int s = 146;
	
	int d = 6;
	
	// number of friends
	
	/**
	 * Private Methods
	 */
	private String[] listCourses = null;
	private String[] listStudents = null;
	private int[][] coursesRating = null; 
	private int[][] friendsRating = null;
	private List<String[]> courseCombinations = null;
	
	private Random rnd = new Random();
	
	/**
	 * - Variables -> X = X(1), X(2), ..., X(n) | X(i) = Student(i)
	 * - Domains -> D = D(1), D(2), ..., D(n) | D(i) = Sub-group of courses (NcK)
	 * 				N = Total Number of Courses, K = Desired Amount of Courses
	 * - Constraints -> Friendship Rating 
	 *   
	 *   Agents Constraints Table: 
	 *    _______________________
	 *   | A / B | {x,y} |{c3,c4}|
	 *   |-------|-------|-------|                                                              
	 *   | {x,y} |   6   |       |                                                         
	 *   |-------|-------|-------|                                                                  
	 *   |{c1,c2}|       | @value = F(A/B)[c1] + F(A/B)[c2]    
	 *   |-------|-------|-------|  if c1 != c3 && c2 != c4
	 *                                  F(A/B) = 0
	 *                          
	 *               
	 *   Example:
	 *   - Variables = Students = 3 -> (Alice, Bob, Charlie)
	 *   - Domains:
	 *     * Total Courses = 4 -> (x, y, z, w)
	 *     * Desired Courses = 2
	 *     * Total Domains = 6 -> ({x,y}, {x,z}, {x,w}, {y,z}, {y,w}, {z,w})
	 *   
	 *    Friendship: (Rating: 1-3) -> Max Friends = 3
	 *    _______________
	 *   | F | A | B | C |
	 *   |---|---|---|---|                                                              
	 *   | A | - | 3 | 0 |                                                                  
	 *   |---|---|---|---|                                                                  
	 *   | B | 1 | - | 3 | 
	 *   |---|---|---|---|
	 *   | C | 2 | 0 | - |
	 *   |---|---|---|---|
	 *   
	 *   Note: (-) equals zero
	 *   
	 *   Courses Rating: (Rating: 1-N) -> Example: N = 4
	 *    ___________________
	 *   | F | X | Y | Z | W |
	 *   |---|---|---|---|---|                                                              
	 *   | A | 4 | 1 | 2 | 3 |                                                               
	 *   |---|---|---|---|---|                                                               
	 *   | B | 3 | 1 | 4 | 2 |
	 *   |---|---|---|---|---|
	 *   | C | 4 | 2 | 3 | 1 |
	 *   |---|---|---|---|---|
	 *   
	 *   Agents Constraints Table: 
	 *               (Agent A constrained with Agent B)                         (Agent B constrained with Agent A)  
	 *    _______________________________________________________     _______________________________________________________
	 *   | A / B | {x,y} | {x,z} | {x,w} | {y,z} | {y,w} | {z,w} |   | B / A | {x,y} | {x,z} | {x,w} | {y,z} | {y,w} | {z,w} |
	 *   |-------|-------|-------|-------|-------|-------|-------|   |-------|-------|-------|-------|-------|-------|-------|
	 *   | {x,y} |   6   |   3   |   3   |   3   |   3   |   0   |   | {x,y} |   2   |   1   |   1   |   1   |   1   |   0   |
	 *   |-------|-------|-------|-------|-------|-------|-------|   |-------|-------|-------|-------|-------|-------|-------|
	 *   | {x,z} |   3   |   6   |   3   |   3   |   0   |   3   |   | {x,z} |   1   |   2   |   1   |   1   |   0   |   1   |
	 *   |-------|-------|-------|-------|-------|-------|-------|   |-------|-------|-------|-------|-------|-------|-------|
	 *   | {x,w} |   3   |   3   |   6   |   0   |   3   |   3   |   | {x,w} |   1   |   1   |   2   |   0   |   1   |   1   |
	 *   |-------|-------|-------|-------|-------|-------|-------|   |-------|-------|-------|-------|-------|-------|-------|
	 *   | {y,z} |   3   |   3   |   0   |   6   |   3   |   3   |   | {y,z} |   1   |   1   |   0   |   2   |   1   |   1   |
	 *   |-------|-------|-------|-------|-------|-------|-------|   |-------|-------|-------|-------|-------|-------|-------|
	 *   | {y,w} |   3   |   0   |   3   |   3   |   6   |   3   |   | {y,w} |   1   |   0   |   1   |   1   |   2   |   1   |
	 *   |-------|-------|-------|-------|-------|-------|-------|   |-------|-------|-------|-------|-------|-------|-------|
	 *   | {z,w} |   0   |   3   |   3   |   3   |   3   |   6   |   | {z,w} |   0   |   1   |   1   |   1   |   1   |   2   |
	 *   |-------|-------|-------|-------|-------|-------|-------|   |-------|-------|-------|-------|-------|-------|-------|
	 *   
	 *   Casting Arguments:
	 *   - Variables: (A=0, B=1, C=2, ...)
	 *   - Courses: (X=0, Y=1, Z=2, W=3)
	 *   - Domains: ({x,y} = [0,1] , {x,z} = [0,2] , {x,w} = [0,3] , {y,z} = [1,2] , {y,w} = [1,3] , {z,w} = [2,3])
	 * 
	 */

	private void readFriendship(String csv) {
		String line = "";  
		String splitBy = ",";  
		Boolean first = false;
		int index = 0;
		try   
		{  
			BufferedReader br = new BufferedReader(new FileReader(csv));  
			while ((line = br.readLine()) != null) 
			{  
				String[] ratings = line.split(splitBy); 
				if(!first) {
					//this.n = ratings.length;
					friendsRating = new int[n][n];
					listStudents = new String[n];
					first = true;
					for(int i=0; i<n; i++) {
						listStudents[i] = ratings[i];
					}
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
	}
	
	private void readCourses(String csv) {
		String line = "";  
		String splitBy = ",";  
		Boolean first = false;
		int index = 0;
		int c_len = 0;
		try   
		{  
			BufferedReader br = new BufferedReader(new FileReader(csv));  
			while ((line = br.readLine()) != null) 
			{  
				String[] ratings = line.split(splitBy); // 9,2,3,5,8,7,1,4,6
				if(!first) {
					c_len = ratings.length;
					coursesRating = new int[n][c_len];
					listCourses = new String[c_len];
					first = true;
					for(int i=0; i<c_len; i++) {
						listCourses[i] = ratings[i];
					}
				}
				else {
					for(int i=0; i<ratings.length; i++) {
						coursesRating[index][i] = Integer.parseInt(ratings[i]);
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
	}
	
	private List<int[]> generate(int n, int k) {
	    List<int[]> combinations = new ArrayList<>();
	    helper(combinations, new int[k], 0, n-1, 0);
	    return combinations;
	}
	
	private void helper(List<int[]> combinations, int data[], int start, int end, int index) {
	    if (index == data.length) {
	        int[] combination = data.clone();
	        combinations.add(combination);
	    } else if (start <= end) {
	        data[index] = start;
	        helper(combinations, data, start + 1, end, index + 1);
	        helper(combinations, data, start + 1, end, index);
	    }
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
	
	private void binaryConstraints(Problem p, int vi, int vj, int cost) {
		for(int i=0; i<n; i++) {
			for(int j=0; j<n; j++) {
				int lastprice = cost * friendsRating[i][j];
				if(lastprice != 0)
					p.setConstraintCost(i, vi, j, vj, lastprice);
			}
		}
	}
	
	private void convertCombinations(List<int[]> combinations) {
		d = combinations.size();
		courseCombinations = new ArrayList<>();
		for(int[] arr: combinations) {
			String[] temp = new String[arr.length];
			int index = 0;
			for(int i: arr) {
				temp[index] = listCourses[i];
				index++;
			}
			courseCombinations.add(temp);
		}
	}
	
	private List<int[]> defineProblem() {
		readFriendship("friendship.csv");
		readCourses("courses.csv");
		List<int[]> combinations = generate(listCourses.length, c);
		convertCombinations(combinations);
		return combinations;
	}
	
	private void initConstraints(Problem p, List<int[]> combinations) {
		/**
		 * In this method you need to change the file path for the following files: friendship.csv, courses.csv 
		 */
		unaryConstraints(p, combinations);
		int[][] coursesCollapse = new int[d][d];
		for(int i=0; i<d; i++) {
			for(int j=0; j<d; j++) {
				coursesCollapse[i][j] = intersectionSize(combinations.get(i), combinations.get(j));
				binaryConstraints(p, i, j, coursesCollapse[i][j]);
			}
		}
	}
	
	private void unaryConstraints(Problem p, List<int[]> combinations) {
		for(int i=0; i<n; i++) {
			for(int j=0; j<combinations.size(); j++) {
				int price = calculateUnary(i, combinations.get(j));
				p.setConstraintCost(i, i, j, price);
			}
		}
//		for(int i=0; i<coursesRating.length; i++) {
//			for(int j=0; j<coursesRating[i].length; j++) {
//				p.setConstraintCost(i, i, j, coursesRating[i][j]);	// (Agent, Agent, Value, Cost)
//			}
//		}
	}
	
	private int calculateUnary(int agent, int[] courses) {
		int price = 0;
		for(int val: courses) {
			price += coursesRating[agent][val];
		}
		return price;
	}
	
	private void write2csv(String filename, List<String[]> list2write) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
			for(String[] str_arr: list2write) {
				writer.write(String.join(",", str_arr));
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private Set<Integer> pick_random_agents(int amount, int limit) {
		Set<Integer> random_picks = new HashSet<Integer>();
		while(random_picks.size() < amount) {
			random_picks.add(rnd.nextInt(limit)+1);
		}
		return random_picks;
	}
	
	private Set<Integer> pick_random_friends(int amount, int self) {
		Set<Integer> random_picks = new HashSet<Integer>();
		while(random_picks.size() < amount) {
			int friend = rnd.nextInt(n);
			if(friend != self)
				random_picks.add(friend);
		}
		return random_picks;
	}
	
	private String[] courses_names(int number) {
		String[] courses_names = new String[number];
		for(int i=0; i<courses_names.length; i++) {
			courses_names[i] = "c" + Integer.toString(i+1);
		}
		return courses_names;
	}
	
	private String[] friends_names() {
		String[] friends_names = new String[n];
		for(int i=0; i<friends_names.length; i++) {
			friends_names[i] = "a" + Integer.toString(i+1);
		}
		return friends_names;
	}
	
	private void generate_problem(Set<Integer> random_picks, String filename) {
		String line = "";
		int index = -1;
		boolean first = false;
		List<String[]> coursesRatings = new ArrayList<>(); 
		List<String[]> friendsRatings = new ArrayList<>(); 
		try   
		{  
			BufferedReader br = new BufferedReader(new FileReader(filename));  
			while ((line = br.readLine()) != null) 
			{  
				index++;
				if(random_picks.contains(index)) {
					String[] ratings = convertRatings(line);
					if(!first) {
						first = true;
						coursesRatings.add(courses_names(ratings.length));
					}
					coursesRatings.add(ratings);
				}
			}
		}
		catch(Exception e) {
			
		}
		write2csv("courses.csv", coursesRatings);
		
		int max_friends = 3;
		friendsRatings.add(friends_names());
		for(int i=0; i<n; i++) {
			int current_friend_rating = 0;
			Set<Integer> friends = pick_random_friends(max_friends, i);
			String[] friends_csv = new String[n];
			for(int j=0; j<friends_csv.length; j++) {
				if(friends.contains(j)) {
					friends_csv[j] = Integer.toString((max_friends - current_friend_rating)*2);
					current_friend_rating++;
				}
				else {
					friends_csv[j] = "0";
				}
			}
			friendsRatings.add(friends_csv);
		}
		write2csv("friendship.csv", friendsRatings);
	}
	
	private String[] convertRatings(String line) {
		String[] courses = line.split(","); // 9,2,5,6,7,8,4,3,1
		String[] ratings = new String[courses.length];
		for(int i=0; i<courses.length; i++) {
			int current_course = Integer.parseInt(courses[i]);
			ratings[current_course-1] = String.valueOf(courses.length-i); 
		}
		return ratings;
	}

	@Override
	public void generate(Problem p, Random rand) {
		//FIRST INITIALIZE THE PROBLEM LIKE THIS:
		//p.initialize(ProblemType.DCOP, n, d);

		//THEN CREATE CONSTRAINTS LIKE THIS:
		//p.setConstraintCost(i, vi, j, vj, cost);

		//DONT USE YOUR OWN RANDOM GENERATOR - USER rand INSTEAD
		//THAT WAY YOUR PROBLEM GENERATOR WILL BE ABLE TO REPRODUCE 
		//A PROBLEM IF IT IS REQUESTED TO DO SO
		generate_problem(pick_random_agents(n, s), "courses.txt");
		List<int[]> combinations = defineProblem();
		p.initialize(ProblemType.DCOP, n, d);
		initConstraints(p, combinations);
		write2csv("combinations.csv", courseCombinations);	
		
	}

	/**
	 * Available Problem Generators:
	 * - Unstructured DCSP: Creates random Distributed Constraint Satisfaction Problems. (Supports: p1, d, n, max-cost)
	 * - Unstructured DCOP: Creates random Distributed Constraint Optimization Problems. (Supports: p1, d, n, max-cost)
	 * - Unstructured ADCOP: Creates random Asymmetric Distributed Constraint Optimization Problems. (Supports: p1, d, n, max-cost)
	 * - Connected DCOP: Creates random Distributed Constraint Optimization Problems that their constraint 
	 *                   relationship graph is a connectivity graph. (Supports: p1, d, n, max-cost)
	 * - Connected DCSP: Creates random Distributed Constraint Satisfaction Problems that their constraint 
	 *                   relationship graph is a connectivity graph. (Supports: p1, d, n, p2)
	 *                   
	 * Attributes:
	 * - n = Number of variables
	 * - d = Domain Size
	 * - max-cost = Maximum cost of a constraint
	 * - p1 = Probability of constraint between two variables
	 * - p2 = Probability of conflict between two constraint variables 
	 * 
	 */
	
	/**
	 * Notes:
	 * - Convert the problem to Max.
	 * - Deploy DSA algorithm Max. using checkCourseLimit function 
	 * - Add courseLimit as problem parameter.
	 */
}

