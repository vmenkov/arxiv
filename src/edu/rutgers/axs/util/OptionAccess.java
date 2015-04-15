package edu.rutgers.axs.util;

public interface OptionAccess {
    public double getDouble(String name, double defVal);
    public long getLong(String name, long defVal);
    public int getInt(String name, int defVal);
    public String getString(String name, String defVal);
    public boolean getBoolean(String name, boolean defVal);
    public  <T extends Enum<T>> T getEnum(Class<T> retType, String name, T defVal);
    public boolean containsKey(String name);
}