package com.apw.Steering;

import com.apw.fakefirm.Arduino;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * <p>Version 2 of steering. This version improves center detection by adding the
 * ability to follow a single lane. This makes crashing much rarer, as well as better
 * navigating tight corners.</p>
 *
 * @author kevin
 * @author carl
 * @author nathan
 */
public class SteeringMk2 extends SteeringBase {

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private int SteerPin;
    private boolean haveNewPixels = false;
    private boolean leftSideFound = false;
    private boolean rightSideFound = false;
    private Arduino theServos; // The servo to write to

    /**
     * Constructor to Start image detection thread, and assign values
     * @param steerPin the pin for servoWrite
     * @param theServos the servo to be writing to
     */
    public SteeringMk2(int steerPin, Arduino theServos) {
        this.SteerPin = steerPin;
        this.theServos = theServos;
        executor.scheduleAtFixedRate(this::makeTurnAdjustment, 20, 50, TimeUnit.MILLISECONDS);
    }

    /**
     * not currently being used.
     * @param pixels the array of pixels.
     * @return Degree to drive to
     */
    @Override
    public int drive(int pixels[]) {
        findPoints(pixels);
        averagePoints();
        return getDegreeOffset();
    }

    /**
     * This gets called every 50ms, writes to the car servos. (drives the car)
     */
    private void makeTurnAdjustment() {
        if (haveNewPixels) {
            theServos.servoWrite(SteerPin, getDegreeOffset() + 90);
        }
    }

    /**
     * Process the camera image, and fill leftPoints, rightPoints, and midPoints.
     * @param pixels An array of pixels
     */
    @Override
    public void findPoints(int[] pixels) {
        clearArrays();
        int midX = cameraWidth / 2; // midX is where the car thinks is the middle of the road
        double distanceAhead = 1.8; // how far ahead the car looks for road. (Eventually dynamic?)
        int screenWidth = 912;


        // Iterate through each row in camera
        for (int cameraRow = screenHeight - 50; cameraRow > (int) (screenHeight / distanceAhead); cameraRow--) {

            // Find left point
            for (int cameraColumn = midX; cameraColumn >= 0; cameraColumn--) {
                if (!leftSideFound && pixels[(screenWidth * (cameraRow)) + cameraColumn] >= 16777215) {
                    leftSideFound = true;
                    leftPoints.add(new Point(cameraColumn, cameraRow));
                    break;
                }
            }

            // Find Right point
            for (int cameraColumn = midX; cameraColumn <= cameraWidth; cameraColumn++) {
                if (!rightSideFound && pixels[(screenWidth * (cameraRow - 1)) + cameraColumn] >= 16777215) {
                    rightSideFound = true;
                    rightPoints.add(new Point(cameraColumn, cameraRow));
                    break;
                }
            }

            // If two Lanes are found, average the two
            if (rightSideFound && leftSideFound) {
                midX = (rightPoints.get(rightPoints.size() - 1).x + leftPoints.get(leftPoints.size() - 1).x) / 2;
                midPoints.add(new Point(midX, cameraRow));

                // If One lane is found, add midpoint 100 pixels towards middle.
            } else if (rightSideFound) {
                double lastY = rightPoints.get(rightPoints.size() - 1).y;
                int lastX = rightPoints.get(rightPoints.size() - 1).x;
                midX = (int) Math.round(lastX - ((cameraWidth) * Math.pow((lastY) / (screenHeight), 2)));
                midPoints.add(new Point(midX, cameraRow));
            } else if (leftSideFound) {
                double lastY = leftPoints.get(leftPoints.size() - 1).y;
                int lastX = leftPoints.get(leftPoints.size() - 1).x;
                midX = (int) Math.round(lastX + ((cameraWidth) * Math.pow((lastY) / (screenHeight), 2)));
                midPoints.add(new Point(midX, cameraRow));

                // If no lanes are found, route towards found lines.
            } else {
                midX = cameraWidth / 2;
                midPoints.add(new Point(midX, cameraRow));
            }

            rightSideFound = false;
            leftSideFound = false;
        }
        averagePoints();
        haveNewPixels = true;
    }

    /**
     * Clear all the arrays
     */
    private void clearArrays() {
        leftPoints.clear();
        rightPoints.clear();
        midPoints.clear();
    }

    /**
     * Average the midpoints to create the steerPoint.
     */
    private void averagePoints() {

        startTarget = (int) (midPoints.size() * 0.5);
        endTarget = (int) (midPoints.size() * 0.7);

        double ySum = 0;
        double xSum = 0;

        // Sum the x's and the y's
        for (int idx = startTarget; idx <= endTarget; idx++) {
            xSum += midPoints.get(idx).x;
            ySum += midPoints.get(idx).y;
        }

        steerPoint.x = (int) (xSum / (endTarget - startTarget));
        steerPoint.y = (int) (ySum / (endTarget - startTarget));
    }

}