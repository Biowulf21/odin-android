package io.hiddemoens.pluskey;
public final class UpdatePolicyTest {
 public static void main(String[] args){
  CoreTest.check(UpdatePolicy.newer("v1.4","1.3"),"new release");
  CoreTest.check(UpdatePolicy.newer("v1.3.1","1.3"),"patch release");
  CoreTest.check(!UpdatePolicy.newer("1.3.0","1.3"),"equal release");
  CoreTest.check(!UpdatePolicy.newer("1.2","1.3"),"no downgrade");
  CoreTest.check(!UpdatePolicy.newer("v1.4-beta","1.3"),"no prerelease tags");
  CoreTest.check(UpdatePolicy.assetUrl("https://github.com/Biowulf21/odin-android/releases/download/v1.4/Odin-1.4.apk"),"own asset");
  CoreTest.check(!UpdatePolicy.assetUrl("https://github.com/other/PlusKey/releases/download/v1.4/a.apk"),"reject other repo");
  CoreTest.check(!UpdatePolicy.assetUrl("http://github.com/Biowulf21/odin-android/releases/download/v1.4/a.apk"),"require https");
  CoreTest.check(!UpdatePolicy.assetUrl("https://github.com/hiddemoens/PlusKey/releases/download/v1.4/a.apk"),"reject upstream asset");
  System.out.println("Update policy tests passed");
 }
}
