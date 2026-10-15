package com.clinic.model;
public final class Admin extends User {
 public Admin(long id,String name,String email){super(id,name,email);}
 @Override public String getRole(){return "ADMIN";}
}
