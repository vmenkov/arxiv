package edu.rutgers.axs.ee4;

import java.io.*;
import java.util.*;
import java.util.regex.*;
//import java.text.*;

import javax.persistence.*;

import org.json.*;

/** This class implements our original assumption: IP = user.
*/

class IPArxivUserInferrer extends ArxivUserInferrer 
{
    String inferUser(String ip_hash, String cookie_hash) {
	fromIPCnt++;
	return ip_hash;
    }
}