package ext.sim.modules;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

	@Variable(name = "n", description = "number of variables", defaultValue = "90")
	int n = 90;

	@Variable(name = "c", description = "number of courses desired", defaultValue = "3")
	int c = 3;

	@Variable(name = "s", description = "number of students", defaultValue = "146")
	int s = 146;

	@Variable(name = "w", description = "weight of friendship", defaultValue = "2")
	int w = 2;

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
	private HashMap<Integer, Integer> swaps = new HashMap<>();

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
			while ((line = br.readLine()) != null && index < n) 
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
	
	private void init_swaps(int size){
        List<Integer> set = new ArrayList<>();
        for(int i=0; i<size; i++){
            set.add(i);
        }
        Collections.shuffle(set);

        for(int i=0; i<size; i++)
            swaps.put(i, set.get(i));
    }
	
	private int[][] swap(int[][] mat){
        int[][] tmp = mat.clone();
        for(int i=0; i<mat.length; i++)
            mat[i] = tmp[swaps.get(i)];
        return mat;
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
	
	private List<String> writeResults(int[][] ratings){
		List<String> results = new ArrayList<>();
		for(int[] i: ratings) {
			results.add(Arrays.toString(i).replace(" ", ""));
		}
		return results;
	}
	
	private void write2csv(String filename, String header, List<String> list2write) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
			writer.write(header);
			writer.newLine();
			for(String str: list2write) {
				writer.write(str.substring(1, str.length()-1));
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private List<int[]> defineProblem() {
		readFriendship("friendship.csv");
		readCourses("courses.csv");
		
		// initiate swapping
		if(friendsRating.length == coursesRating.length)
			init_swaps(friendsRating.length);
		else
			System.exit(1); // Validate that the coursesRating and friendsRating have the same length otherwise we have a bug!
		
		// perform swapping
		friendsRating = swap(friendsRating);
		coursesRating = swap(coursesRating);
		
		String friendsHeader = "";
		for(int i=0; i<friendsRating[0].length; i++) {
			friendsHeader += ("a" + Integer.toString(i+1) + ",");
		}
		friendsHeader = friendsHeader.substring(0, friendsHeader.length()-1);
		write2csv("friendship_swap.csv", friendsHeader, writeResults(friendsRating));
		
		String coursesHeader = "";
		for(int i=0; i<coursesRating[0].length; i++) {
			coursesHeader += ("c" + Integer.toString(i+1) + ",");
		}
		coursesHeader = coursesHeader.substring(0, coursesHeader.length()-1);
		write2csv("courses_swap.csv", coursesHeader, writeResults(coursesRating));
		
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
				//				System.out.println("ID: " + Integer.toString(i) + ", Assignment: " + Integer.toString(j) + ", Price: " + Integer.toString(price));
				p.setConstraintCost(i, i, j, price);
			}
		}
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

	private Set<Integer> pick_random_agents(int amount, int limit, Random rnd) {
		Set<Integer> random_picks = new HashSet<Integer>();
		while(random_picks.size() <= amount) {
			random_picks.add(rnd.nextInt(limit)+1);
		}
		return random_picks;
	}

	private Set<Integer> pick_random_friends(int amount, int self, Random rnd) {
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

	private void generate_problem(Set<Integer> random_picks, String filename, Random rnd) {
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
			Set<Integer> friends = pick_random_friends(max_friends, i, rnd);

			List<Integer> ratings = range(1, friends.size());
			Collections.shuffle(ratings, new Random());

			ArrayList<Integer> friends_rating = new ArrayList<Integer>(ratings);
			String[] friends_csv = new String[n];

			index = 0;
			for(int j=0; j<friends_csv.length; j++) {
				if(friends.contains(j)) {
					friends_csv[j] = Integer.toString(friends_rating.get(index++) * w);
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

		// Uncomment the line below to Generate problem using the simulated data set attached
		// generate_problem(pick_random_agents(n, s, rand), "courses.txt", rand);

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

