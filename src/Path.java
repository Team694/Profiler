import java.util.ArrayList;


public class Path {
	//Trajectory contains all the calculated points while waypoints are just the points the user provided
	//Trajectory has all the points while waypoint only stores the points the user gave in (x, y, and heading) 
	//curvepoints stores the points that define each curve
	ArrayList<Waypoint> centralTrajectory;
	Waypoint[] waypoints;  
	Waypoint[][] curvepoints; 
	int numberOfPoints; //The number of points per curve
	double dt; 
	double totalDistance; 
	double totalTime; 
	double maxVelocity; 
	double maxAcceleration; 
	double maxJerk;
	double wheelBaseWidth; //The offset for the paths is half the wheel base width
	ArrayList<Waypoint> leftTrajectory, rightTrajectory; 
	Waypoint[] leftWaypoints, rightWaypoints; 
	Waypoint[][] leftCurve, rightCurve;  
	
	//Enter information for the center of the robot 
	public Path(int numberOfPoints, double dt, double wheelBaseWidth, double maxVelocity, double maxAcceleration, double maxJerk, Waypoint... waypoints) {
		if(waypoints.length < 2) {
			System.out.println("Not enough points");
			System.exit(0);
		} 
		this.numberOfPoints = numberOfPoints;
		this.dt = dt; 
		this.wheelBaseWidth = wheelBaseWidth; 
		this.maxVelocity = maxVelocity; 
		this.maxAcceleration = maxAcceleration; 
		this.maxJerk = maxJerk; 
		this.waypoints = waypoints; 
		this.curvepoints = new Waypoint[waypoints.length - 1][4]; 
		leftCurve = new Waypoint[waypoints.length - 1][4]; rightCurve = new Waypoint[waypoints.length - 1][4];  
		centralTrajectory = new ArrayList<Waypoint>();
		leftTrajectory = new ArrayList<Waypoint>(); rightTrajectory = new ArrayList<Waypoint>();
		findPaths(); 
		findCentralTrajectory();
		findSideTrajectories();
		//parameterizeTrajectories();
	}

	//Finds a point by using cubic bezier
	/*
	 * @param the first point on the line
	 * @param the first control point 
	 * @param the second control point 
	 * @param the second point on the line
	 * @param the percentage of the way on the curve
	 * @param the percentage of the way on the curve 
	 */
	Point cubicBezier(Point point1, Point control1, Point control2, Point point2, double alpha) {
		double x = point1.x * Math.pow(1 - alpha, 3) + control1.x * 3 * Math.pow(1 - alpha, 2) * alpha + 
					control2.x * 3 * (1 - alpha) * Math.pow(alpha, 2) + point2.x * Math.pow(alpha, 3);
		double y = point1.y * Math.pow(1 - alpha, 3) + control1.y * 3 * Math.pow(1 - alpha, 2) * alpha + 
					control2.y * 3 * (1 - alpha) * Math.pow(alpha, 2) + point2.y * Math.pow(alpha, 3);
		return new Point(x, y); 
	}
	
	//Finds all the points on the path
	/*
	 * @param the number of points between each pair of waypoints
	 * @param the tightness of the curve
	 * @param the trajectory on which to load the points
	 * @param the array on which to load the curvepoints
	 * @param the waypoints from which the path with be generated
	 */
	private void genBezierPath(int numberOfPoints, double tightness, ArrayList<Waypoint> trajectory, Waypoint[][] curvepoints, Waypoint... waypoints) {
		trajectory.add(waypoints[0]);
		for(int i = 0; i < waypoints.length - 1; i++) {
			Waypoint startwp = waypoints[i]; 
			Waypoint endwp = waypoints[i + 1];
			double distance = startwp.distanceTo(endwp);
			double gplength = distance / 2 * tightness; 
			Point startOffset = Point.PolarPoint(gplength, startwp.heading);
			Point endOffset = Point.PolarPoint(-gplength, endwp.heading);
			Point control1 = startwp.offset(startOffset.x, startOffset.y);
			Point control2 = endwp.offset(endOffset.x, endOffset.y);
			
			curvepoints[i][0] = startwp; 
			curvepoints[i][1] = control1.toWaypoint();
			curvepoints[i][2] = control2.toWaypoint();
			curvepoints[i][3] = endwp;  
			for(int j = 1; j < numberOfPoints; j++) {
				double percentage = (double) j / (double) numberOfPoints;
				Point pathPoint = cubicBezier(startwp, control1, control2, endwp, percentage); 
				trajectory.add(pathPoint.toWaypoint());
			}
			trajectory.add(endwp);
			//The latest added point which is a path point is true
		}
	}

	private Waypoint[] getOffsetWaypoints(double offset) {
		if(this.waypoints.length < 1) {
			System.out.println("Not enough points"); 
			return this.waypoints; 
		}

		Waypoint[] waypoints = new Waypoint[this.waypoints.length]; 
		double change = 0.1; 
		if(offset < 0) {
			change *= -1; 
		}else if(offset == 0) {
			return this.waypoints; 
		}
		double accuracy = 0.3; 
		Orientation orientation; 
		Orientation last = Orientation.findOrientation(curvepoints[0][3].heading); 

		for(int i = 0; i < curvepoints.length; i++) {
			orientation = Orientation.findOrientation(curvepoints[i][3].heading);
			if(orientation == last) {
				Point pointA = curvepoints[i][0].offsetPerpendicular(curvepoints[i][1], offset, change, accuracy, orientation);
				waypoints[i] = new Waypoint(pointA.x, pointA.y, this.waypoints[i].heading);
			} 
			Point pointB = curvepoints[i][3].offsetPerpendicular(curvepoints[i][2], offset, change, accuracy, orientation);
			waypoints[i + 1] = new Waypoint(pointB.x, pointB.y, this.waypoints[i + 1].heading); 
			last = orientation; 
		}

		return waypoints; 
	}

	//Finds the paths not the trajectories 
	//That means only positions
	public void findPaths() {
		genBezierPath(numberOfPoints, 0.8, centralTrajectory, curvepoints, waypoints);
		
		double offset = wheelBaseWidth / 2; 
		leftWaypoints = getOffsetWaypoints(-offset); rightWaypoints = getOffsetWaypoints(offset);
		genBezierPath(numberOfPoints, 0.8, leftTrajectory, leftCurve, leftWaypoints); genBezierPath(numberOfPoints, 0.8, rightTrajectory, rightCurve, rightWaypoints);
	}

	//Gets the distances from the start, of each waypoint
	//Requires that all the curvepoints be generated already
	private void getDistancesFromStart(ArrayList<Waypoint> trajectory) {
		double distanceAccumlator = 0; 
		trajectory.get(0).distanceFromStart = distanceAccumlator;
		for(int i = 1; i < trajectory.size(); i++) {
			double distance = trajectory.get(i).distanceTo(trajectory.get(i - 1));
			distanceAccumlator += distance; 
			trajectory.get(i).distanceFromStart = distanceAccumlator; 
		}
		totalDistance = trajectory.get(trajectory.size() - 1).distanceFromStart; 
	}
	
	//Gets the distances from the end, of each waypoint
	//Requires that the distances from the start be calculated
	private void getDistancesFromEnd(ArrayList<Waypoint> trajectory) {
		double totalDistance = trajectory.get(trajectory.size() - 1).distanceFromStart; 
		for(int i = 0; i < trajectory.size(); i++) {
			trajectory.get(i).distanceFromEnd = totalDistance - trajectory.get(i).distanceFromStart;
		}
	}
	
	//Gets the headings of each waypoint
	//Requires that all the curvepoints be generated already
	private void getHeadings(ArrayList<Waypoint> trajectory) {
		for(int i = 0; i < trajectory.size() - 1; i++) {
			trajectory.get(i).getHeading(trajectory.get(i + 1));
		}
	}
	
	//Gets the velocity and acceleration of the curvepoints under a trapezodial motion profile for the central path
	//Requires that the distances be found
	//To do velocity corrections angular velocities must be found
	private void getVelocities() {
		for(int i = 0; i < centralTrajectory.size(); i++) { 
			//Gets the velocity for the accelerating, cruise, and decelerating cases
			//Using the kinematic equation Vf^2 = Vi^2 + 2ad
			double accelVel = Math.sqrt(2 * maxAcceleration * centralTrajectory.get(i).distanceFromStart);
			double cruiseVel = maxVelocity; 
			double decelVel = Math.sqrt(2 * maxAcceleration * centralTrajectory.get(i).distanceFromEnd);
			
			//Sets the velocity to the minium of these
			if(accelVel < cruiseVel && accelVel < decelVel) {
				centralTrajectory.get(i).velocity = accelVel;  
			}else if(cruiseVel < accelVel && cruiseVel < decelVel) {
				centralTrajectory.get(i).velocity = cruiseVel;  
			}else if(decelVel < accelVel && decelVel < cruiseVel) {
				centralTrajectory.get(i).velocity = decelVel;
			} 
		}
	}
	
	//Gets the time by which it will take the robot to be at that point
	//Requires that the distances and velocities be found
	private void getTimes(ArrayList<Waypoint> trajectory) {
		double timeAccumlator = 0; 
		trajectory.get(0).time = timeAccumlator; 
		for(int i = 1; i < trajectory.size(); i++) {
			double distanceTraveled = trajectory.get(i).distanceFromStart - trajectory.get(i - 1).distanceFromStart;
			double avgVelocity = (trajectory.get(i).velocity + trajectory.get(i - 1).velocity) / 2; 
			double timeElapsed = distanceTraveled / avgVelocity;
			timeAccumlator += timeElapsed; 
			trajectory.get(i).time = timeAccumlator; 
		}
		totalTime = trajectory.get(trajectory.size() - 1).time; 
	}

	//Gets the accelerations for the central path
	//Requires that velocities and times be found
	private void getAccelerations() {
		centralTrajectory.get(0).acceleration = maxAcceleration;
		for(int i = 1; i < centralTrajectory.size(); i++) {
			double velocityDiff = centralTrajectory.get(i).velocity - centralTrajectory.get(i - 1).velocity;
			if(velocityDiff == 0) {
				centralTrajectory.get(i).acceleration = 0; 
				continue; 
			} 
			double timeDiff = centralTrajectory.get(i).time - centralTrajectory.get(i - 1).time; 
			centralTrajectory.get(i).acceleration = velocityDiff / timeDiff;
			if(centralTrajectory.get(i).acceleration > maxAcceleration) {
				centralTrajectory.get(i).acceleration = maxAcceleration; 
			}else if(centralTrajectory.get(i).acceleration < -maxAcceleration) {
				centralTrajectory.get(i).acceleration = -maxAcceleration;
			} 
		} 
	}
	
	//Gets the jerk of each waypoint
	//Requires that accelerations and times be calculated
	private void getJerks() {
		for(int i = 0; i < centralTrajectory.size() - 1; i++) {
			double accelerationChange = centralTrajectory.get(i + 1).acceleration - centralTrajectory.get(i).acceleration; 
			double timeElapsed = centralTrajectory.get(i + 1).time - centralTrajectory.get(i).time; 
			double jerk = accelerationChange / timeElapsed; 
			if(jerk > maxJerk) { 
				jerk = maxJerk; 
			}else if(jerk < -maxJerk) {
				jerk = -maxJerk; 
			}
			centralTrajectory.get(i).jerk = jerk; 
		}
		centralTrajectory.get(centralTrajectory.size() - 1).jerk = 0; 
	}

	//Gets the angular velocity at each point
	//Requires that the headings and times be found
	//This is used for velocity correction, and to find the velocities of the side trajectories
	private void getAngularVelocities(ArrayList<Waypoint> trajectory) {
		trajectory.get(0).angularVelocity = 0;   
		for(int i = 1; i < trajectory.size(); i++) {
			double headingDiff = trajectory.get(i).heading - trajectory.get(i - 1).heading; 
			double timeDiff = trajectory.get(i).time - trajectory.get(i - 1).time; 
			trajectory.get(i).angularVelocity = headingDiff / timeDiff;  
		}
	}

	//Finds the trajectory of the center of the robot 
	//Paths must be made first
	public void findCentralTrajectory() {
		getDistancesFromStart(centralTrajectory); 
		getDistancesFromEnd(centralTrajectory); 
		getHeadings(centralTrajectory);
		getVelocities();
		getTimes(centralTrajectory);
		getAccelerations();  
		getAngularVelocities(centralTrajectory);
		getJerks(); //60 ft/sec^3 best for trapezodial motion profile
	}

	//Copies the headings from the central trajectory to the side
	/*
	 *@param the side trajectory on which to copy the headings onto
	 */
	private void copyHeadings(ArrayList<Waypoint> side) {
		for(int i = 0; i < centralTrajectory.size() && i < side.size(); i++) { 
			side.get(i).heading = centralTrajectory.get(i).heading; 
		}
	}

	//Finds the velocities of the side wheels
	//Requires that the angular velocities of the central trajectory be found
	//And that the side paths be found
	private void getSideVelocities() {
		//Iterating through the points of the trajectory
		for(int i = 0 ; i < centralTrajectory.size(); i ++) {
			//Find the difference the sides have to be to be spinning at that angular velocity
			double velocityDifference = (wheelBaseWidth / 2) * centralTrajectory.get(i).angularVelocity; 
			if(leftTrajectory.get(i).distanceFromStart > rightTrajectory.get(i).distanceFromStart) {
				//If left side is outside it is greater
				leftTrajectory.get(i).velocity = centralTrajectory.get(i).velocity + velocityDifference; 
				rightTrajectory.get(i).velocity = centralTrajectory.get(i).velocity - velocityDifference; 
			}else if(rightTrajectory.get(i).distanceFromStart > leftTrajectory.get(i).distanceFromStart) {
				//If right side is outside it is greater
				rightTrajectory.get(i).velocity = centralTrajectory.get(i).velocity + velocityDifference;
				leftTrajectory.get(i).velocity = centralTrajectory.get(i).velocity - velocityDifference; 
			}else {
				//Otherwise its all equal and straight
				leftTrajectory.get(i).velocity = centralTrajectory.get(i).velocity; 
				rightTrajectory.get(i).velocity = centralTrajectory.get(i).velocity; 
			}
		}
	}

	//Finds the side accelerations
	//Difference between this and the central is that it ignores the maximum acceleration
	/*
	 *@param the side trajectory
	 */
	private void getSideAccelerations(ArrayList<Waypoint> side) {
		double velocityDiffI = side.get(1).velocity - side.get(0).velocity;
		double timeDiffI = side.get(1).time - side.get(0).time;  
		side.get(0).acceleration = velocityDiffI / timeDiffI;  
		for(int i = 1; i < side.size(); i++) {
			double velocityDiff = side.get(i).velocity - side.get(i - 1).velocity; 
			double timeDiff = side.get(i).time - side.get(i - 1).velocity;
			side.get(i).acceleration = velocityDiff / timeDiff;  
		}
	}

	//Finds the side Jerks
	//Difference between this and the central is that it ignores the maximum jerk
	/*
	 *@param the side trajectory
	 */ 
	private void getSideJerks(ArrayList<Waypoint> side) {
		double accelDiffI = side.get(1).acceleration - side.get(0).acceleration;
		double timeDiffI = side.get(1).time - side.get(0).time;  
		side.get(0).jerk = accelDiffI / timeDiffI;  
		for(int i = 1; i < side.size(); i++) {
			double accelDiff = side.get(i).acceleration - side.get(i - 1).acceleration; 
			double timeDiff = side.get(i).time - side.get(i - 1).velocity;
			side.get(i).jerk = accelDiff / timeDiff;  
		}
	}

	//Finds the trajectories for the sides of the robot 
	//Paths must be made first
	public void findSideTrajectories() { 
		getDistancesFromStart(leftTrajectory); getDistancesFromStart(rightTrajectory);
		getDistancesFromEnd(leftTrajectory); getDistancesFromEnd(rightTrajectory);
		copyHeadings(leftTrajectory); copyHeadings(rightTrajectory);
		getSideVelocities();
		getTimes(leftTrajectory); getTimes(rightTrajectory);
		getSideAccelerations(leftTrajectory); getSideAccelerations(rightTrajectory);
		getSideJerks(leftTrajectory); getSideJerks(rightTrajectory);   
	}

	private void timeParameterize(ArrayList<Waypoint> trajectory, Waypoint[][] curvepoints) {
		ArrayList<Waypoint> copy = new ArrayList<Waypoint>(); 
		for(int i = 0; i < trajectory.size(); i++) {
			copy.add(trajectory.get(i)); 
		}
		trajectory.clear();
		double totalTime = copy.get(copy.size() - 1).time;
		int index = 0;  
		double percentage = 0;
		int curve;  
		for(double time = 0; time <= totalTime; time += dt) {
			for(int i = index; i < copy.size() - 1; i++) { 
				if(time == copy.get(i).time) {
					index = i;
					percentage = (double) i / (double) numberOfPoints;   
					break; 
				}else if(copy.get(i).time < time && copy.get(i + 1).time > time) {
					index = i; 
					double timeDifference = copy.get(i + 1).time - copy.get(i).time; 
					double timeNeeded = time - copy.get(i).time;
					double percentNeeded = timeNeeded / timeDifference;
					percentage = (double) (i + percentNeeded) / (double) numberOfPoints; 
					break;   
				}
			}
			curve = index % numberOfPoints;
			if(curve >= curvepoints.length) break; 
			System.out.println(curve + "-" + percentage);  
			Waypoint point = cubicBezier(curvepoints[curve][0], curvepoints[curve][1], curvepoints[curve][2], curvepoints[curve][3], percentage).toWaypoint();
			trajectory.add(point); 
		}
	}

	public void parameterizeTrajectories() {
		timeParameterize(centralTrajectory, curvepoints);
		findCentralTrajectory();
		timeParameterize(leftTrajectory, leftCurve);
		timeParameterize(rightTrajectory, rightCurve);
		findSideTrajectories();
	}
}