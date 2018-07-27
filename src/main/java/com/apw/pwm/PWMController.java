package com.apw.pwm;

/**
 * Members must conform to a singleton pattern.
 */
public interface PWMController {

  /**
   * Singleton accessor. This method must be overriden by the member.
   *
   * @return The single instance of the PWMController.
   */
  static PWMController getInstance() {
    System.err.println("Error: singleton accessor was not overriden by member.");
    return null;
  }

  /**
   * Set the angle of a servo. By default, calls {@link #setOutputPulseWidth(int, double)}
   * normalized to 1-2 ms.
   *
   * @param pin Non-negative integer representing the pin number. Range dependent on
   * implementation.
   * @param angle Double from 0 to 180 inclusive representing the angle of the servo.
   */
  default void setServoAngle(int pin, double angle) {
    setOutputPulseWidth(pin, 1 + angle / 180);
  }

  /**
   * Set the pulse width of the output.
   *
   * @param pin Non-negative integer representing the pin number. Range dependent on
   * implementation.
   * @param ms The width of the pulse in milliseconds.
   */
  void setOutputPulseWidth(int pin, double ms);

  void close();

}
