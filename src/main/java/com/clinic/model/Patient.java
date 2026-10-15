package com.clinic.model;
public final class Patient extends User {
 public Patient(long id,String name,String email){super(id,name,email);}
 @Override public String getRole(){return "PATIENT";}
}
