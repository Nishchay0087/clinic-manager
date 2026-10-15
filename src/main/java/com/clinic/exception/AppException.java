package com.clinic.exception;
public class AppException extends RuntimeException {
 private final int status;
 public AppException(int status,String message){super(message);this.status=status;}
 public int getStatus(){return status;}
}
