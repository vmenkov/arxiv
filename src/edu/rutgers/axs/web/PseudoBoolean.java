package edu.rutgers.axs.web;

/** Substitute for Boolean. Used so that all types here are enum types */
enum PseudoBoolean {
    False, True;
    boolean booleanValue() {
	return (this==True);
    }
};

