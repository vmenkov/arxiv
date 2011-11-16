package edu.rutgers.axs.sql;

import java.util.*;
import java.lang.reflect.*;
import java.lang.annotation.*;

/** An annotation to add explanatory text to enum values in data entry forms.

    For access functions, see {@link edu.rutgers.axs.sql.Util}
*/
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EA {
    /** Auxiliary explanatory text, which appears in displays <em>after</em> the
     * button name */
    String value() default "";
    /** Text that serves as the replacement for the constant's name
      (i.e., it appears in displays <em>instead of</em> the button name) */
    String alt() default "";
    /** If supplied, a value like this should be rejected by the validator */
    boolean illegal() default false;
    /** If supplied, null should be stored instead */
    boolean storeNull() default false;
    /** Mandatory class (which should be defined in a CSS file somewhere) */
    String style() default "";
}

