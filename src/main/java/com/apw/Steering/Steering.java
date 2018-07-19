package com.apw.Steering;

import com.apw.apw3.DriverCons;

public class Steering {

	
	public int startingPoint = 0;
	
	//767 is white

	public int heightOfArea = 32;
	public int startingHeight = 272;

	private int screenWidth = 912;
	private int cameraWidth = 640;
	private int screenHeight = com.apw.apw3.DriverCons.D_ImHi;
	public Point steerPoint = new Point(0, 0);

	public Point[] leadingMidPoints = new Point[startingHeight + heightOfArea];
	public Point[] leftPoints = new Point[heightOfArea];
	public Point[] rightPoints = new Point[heightOfArea];
	
	public Point[] midPoints = new Point[heightOfArea];
	Point origin = new Point(cameraWidth/2, screenHeight);
	
	Boolean found = false;
	Boolean leftSideFound = false;
	Boolean rightSideFound = false;

	public Steering() {
		for (int i = 0; i<heightOfArea; i++) {
			leftPoints[i] = new Point(0, 0);
			rightPoints[i] = new Point(0, 0);
			midPoints[i] = new Point(0, 0);
		}
		for (int i = 0; i<leadingMidPoints.length; i++) {
			leadingMidPoints[i] = new Point(0, 0);
		}
	}
	
	public Point[] findPoints(int[] pixels) {
		int roadMiddle = cameraWidth;
		int leftSideTemp = 0;
		int rightSideTemp = 0;
		startingPoint = 0;
		
		//first, find where road starts on both sides
		leftSideFound = false;
		rightSideFound = false;
		for (int i = screenHeight - 22; i>startingHeight + heightOfArea; i--) {
			
			for (int j = roadMiddle/2; j>=0; j--) {
				if (pixels[(screenWidth * (i)) + j] == 16777215) {
					leftSideFound = true;
					break;
				}
			}
			for (int j = roadMiddle/2; j<cameraWidth; j++) {
				if (pixels[(screenWidth * (i)) + j] == 16777215) {
					rightSideFound = true;
					break;
				}
			}

			if (leftSideFound && rightSideFound) {
				startingPoint = i;
				leftSideFound = false;
				rightSideFound = false;
				break;
			}
			leftSideFound = false;
			rightSideFound = false;
		}
		
		//Next, calculate the roadpoint 
		
		int count = 0;
		
		for (int i = startingPoint; i > startingHeight + heightOfArea; i--) {
			for (int j = roadMiddle/2; j>=0; j--) {
				if (pixels[screenWidth * i + j] == 16777215) {
					leftSideTemp = j;
					break;
				}
			}
			for (int j = roadMiddle/2; j<cameraWidth; j++) {
				if (pixels[screenWidth * i + j] == 16777215) {
					rightSideTemp = j;
					break;
				}
			}
			
			leadingMidPoints[count].x = roadMiddle / 2;
			leadingMidPoints[count].y = i;
			count++;
			roadMiddle = leftSideTemp + rightSideTemp;
		}
		System.out.println("\n\n\n" + count + "\n\n\n");
		count = 0;
		for (int i = startingHeight + heightOfArea; i>startingHeight; i--) {
			//center to left
			found = false;
			leftPoints[count].y = i;
			
			for (int j = roadMiddle/2; j>=0; j--) {
				if (pixels[screenWidth * i + j] == 16777215) {
					leftPoints[count].x = j;
					found = true;
					break;
				}
				
			}
			if (found == false) {
				leftPoints[count].x = 0;
			}
			
			
			//center to right
			found = false;
			rightPoints[count].y = leftPoints[count].y;
			for (int j = roadMiddle/2; j<cameraWidth; j++) {
				if (pixels[screenWidth * i + j] == 16777215) {
					rightPoints[count].x = j;
					found = true;
					break;
				}
			}
			if (found == false) {
				rightPoints[count].x = cameraWidth;
			}
			
			midPoints[count].x = roadMiddle/2;
			midPoints[count].y = (leftPoints[count].y);
			roadMiddle = (leftPoints[count].x + rightPoints[count].x);
			count++;
		}
		return midPoints;
		
	}
	
	public double curveSteepness(double turnAngle) {
		return turnAngle/(90);
	}


	/*
	find the average point from the midpoints array
	 */
	public void averageMidpoints() {
        double tempY = 0;
        double tempX = 0;
        int tempCount = 0;

        // Sum the x's and the y's
	    for (int i = 0; i<startingPoint - (startingHeight + heightOfArea); i++) {
	    		Point point = new Point (leadingMidPoints[i].x, leadingMidPoints[i].y);
            tempX += point.x;
            tempY += point.y;
            tempCount++;
        }
	    if (tempCount == 0) {
		    for (int i = 0; i<midPoints.length; i++) {
	    			Point point = new Point (midPoints[i].x, midPoints[i].y);
	    			tempX += point.x;
	    			tempY += point.y;
	    			tempCount++;
		    }
	    }

        steerPoint.x = (int)(tempX / tempCount);
	    steerPoint.y = (int)(tempY / tempCount);

    }


    public int getDegreeOffset() {
	    int xOffset = origin.x - steerPoint.x;
	    int yOffset = Math.abs(origin.y - steerPoint.y);

	    int tempDeg = (int)((Math.atan2(-xOffset, yOffset)) * (180 / Math.PI));
	    

	    return (int)((Math.atan2(-xOffset, yOffset)) * (180 / Math.PI));
    }
    
    //placeholder
    public int getEstimatedSpeed() {
    		return 5;
    }
}

