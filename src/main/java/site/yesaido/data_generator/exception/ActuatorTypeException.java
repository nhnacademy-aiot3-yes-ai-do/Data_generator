package site.yesaido.data_generator.exception;

import java.io.Serial;

public class ActuatorTypeException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  public ActuatorTypeException(String message, Throwable cause) {
    super(message, cause);
  }
  public ActuatorTypeException(String message) {
        super(message);
    }
}
