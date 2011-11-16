package edu.rutgers.axs.sql;

import java.lang.reflect.*;
import java.lang.annotation.*;

public class ShowAnnotations {
   public static void main(String[] args) throws Exception {

       for (Method m : Class.forName(args[0]).getMethods()) {	   
	   Annotation[] an=m.getDeclaredAnnotations(); 
	   
	   System.out.print("method " + m + " annotations: ");
	   for(Annotation a: an) System.out.print("" + a + "; ");
	   System.out.println();	   
       }
      
       
       for (Field f : Class.forName(args[0]).getDeclaredFields()) {
	   Annotation[] an=f.getDeclaredAnnotations(); 
	   
	   System.out.print("field " + f + " annotations: ");
	   for(Annotation a: an) System.out.print("" + a + "; ");
	   System.out.println();
       }
   }
}
