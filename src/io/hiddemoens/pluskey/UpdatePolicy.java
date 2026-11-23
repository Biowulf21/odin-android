package io.hiddemoens.pluskey;
final class UpdatePolicy {
 static final String REPOSITORY = "Biowulf21/odin-android";
 static boolean newer(String release,String current){
  if(release==null||current==null)return false;
  release=release.replaceFirst("^v","");
  if(!release.matches("[0-9]{1,6}(\\.[0-9]{1,6}){0,3}")||!current.matches("[0-9]{1,6}(\\.[0-9]{1,6}){0,3}"))return false;
  String[] a=release.split("\\."),b=current.split("\\.");
  for(int i=0;i<Math.max(a.length,b.length);i++){int x=i<a.length?Integer.parseInt(a[i]):0,y=i<b.length?Integer.parseInt(b[i]):0;if(x!=y)return x>y;}return false;
 }
 static boolean assetUrl(String url) {
  String releasePrefix = java.util.regex.Pattern.quote("https://github.com/" + REPOSITORY + "/releases/download/");
  return url != null && url.matches(releasePrefix + "[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\\.apk");
 }
}
