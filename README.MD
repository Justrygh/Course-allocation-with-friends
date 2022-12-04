# Course Allocation With Friendship

### Authors: Ilya Khakhiashvili & Dr.Tal Grinshpoun & Dr.Lihi Dery

## Documentation
This project was based on the following Java Simulator to model the problem as 
**D**istribued **C**onstraint **O**ptimization **P**roblem (DCOP).
https://docs.google.com/document/d/1B19TNQd8TaoAQVX6njo5v9uR3DBRPmFLhZuK0H9Wiks/edit?usp=sharing

## Problem Definition

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
     *
     *    Agent A Unary Constraints Table:
     *    ____________________________________________
	 *   | F | {X,Y,Z} | {X,Y,W} | {X,Z,W} | {Y,Z,W} |
	 *   |---|---------|---------|---------|---------|                                                        
	 *   | A | 4+1+2=7 | 4+1+3=8 | 4+2+3=9 | 1+2+3=6 |                                                               
	 *   |---|---------|---------|---------|---------|  
	 *
     *
	 *   Agents Binary Constraints Table: 
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

- Unary Constraint: Each agent has a unary table that indicates his courses preferences.
- Binary Constraints: Each couple of students have a binary table represent their friendship.
- Global Constraints: Each course has a capacity limit.

## Source Code
**Java**: This code contains the agents & the module to simulate out problem
based on the Google Docs described above. 

List of agents available:

- **DSA_RC** - Our presented algorithm.
- **DSA** - **D**istributed **S**tochastic Search **A**lgorithm
- **Greedy**
- **HBS** - **H**arvard **B**usiness **S**chool
- **Iterative** - **R**andom **S**erial **D**ictatorship (RSD)
- **Random**

Make sure to create the following directories in the java project (for each agent used in the problem):
- e.g., *Iterative_Agent* or *Iterative_Unary_Agent* (Depends on the use)

The experiment will output csv files for each agent based on the test.xml file.
The CSV files contains the following headers (descending order):

- Total Utility
- Illegal Assignments
- Gini Coefficient
- Number of friends
- First Agent Utility
- Middle Agent Utility
- Last Agent Utility

The default output will result to csv files for 40,45,...,90agents.csv and contains 50 samples for each experiment.

**Python**: This code takes the csv results created using the java and plots graphs in python.

To be continued...