package com.clinic.model;
public final class Doctor extends User {
 public Doctor(long id,String name,String email){super(id,name,email);}
 @Override public String getRole(){return "DOCTOR";}
}
