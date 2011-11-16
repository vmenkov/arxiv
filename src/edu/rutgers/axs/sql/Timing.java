package edu.rutgers.axs.sql;

import java.util.*;
//import javax.persistence.*;
//import edu.rutgers.axs.web.Logging;
import java.lang.reflect.*;
import java.lang.annotation.*;

/** An annotation of this kind can be added to Date-valued fields, to indicate
    what kind of datetime values are going to be stored there (just the date,
    or date and time, with a particular interval). This is used to generate
    proper HTML input forms
*/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Timing {
    Interval interval() default edu.rutgers.axs.sql.Timing.Interval.Daily;

    public static enum Interval {
	Daily,
	HalfHourly;
    }

}

