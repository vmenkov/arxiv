<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
 Participation  main=new Participation (request,response);
%>

<!-- © 2011 by AEP -->
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<!-- #BeginTemplate "_technical/templates/Template.dwt" -->

<head>
<!-- #BeginEditable "doctitle" -->
<title>arXiv - Participation Page</title>
<!-- #EndEditable -->
<meta name="Keywords" content="" />
<meta name="Description" content="Here goes the description of arXiv for search engines" />
<meta name="Author" content="pichugin@eden.rutgers.edu" />
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta http-equiv="Content-Style-Type" content="text/css" />

<link rel="stylesheet" type="text/css" href="_technical/styles/styles_all.css" />
<!--[if lte IE6]>
<link rel="stylesheet" type="text/css" href="_technical/styles/styles_ie.css" /><![endif]-->

<link rel="icon" type="image/x-icon" href="favicon.ico" />
<script type="text/javascript" src="_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="_technical/scripts/jquery-transitions.js"></script>

<script type="text/javascript">
</script>

<!-- #BeginEditable "Styles" -->

<style type="text/css">
.question {font-weight:bold; margin:0px 0px 5px 20px; text-indent:-15px;}
.diamond {vertical-align:bottom; font-size:1.2em; color:red;}
.answer {margin:0px 0px 5px 18px}

.button
{width:170px}

#anchor {
font-size:1px;
width:1px;
height:1px;
background:inherit;
color:inherit;
border:none;
margin:none;
}

</style>

<script type="text/javascript">

function StartScripts() { 
BlurLinks();
}

window.onload = StartScripts;
</script>


<!-- #EndEditable -->

</head>

<body>
<%   if (main.error) {   %>  <%@include file="../include/error.jsp"%>
<%   } else {        
%>


<div id="upper_frame">

	<img src="_technical/images/bar-rutgers.jpg" style="border:none; width:1000px; height:75px; position:absolute; top:0px; left:0px;" alt="" />
	<div style="position:absolute; top:10px; left:10px; font-size:4em; font-weight:bold; text-shadow: 2px 2px 2px #000; right: 813px;"><span style="color:#CCCCCC; font-weight:normal;">my:</span>arXiv</div>
	

	<div id="validator" style="width:3px; height:3px; position:absolute; top:0px; right:0px; color:blue;">
		<a target="_blank" href="http://validator.w3.org/check?uri=referer" style="color:#e51937; background:inherit;">.</a>
	</div>

	<div id="validator_css" style="width:3px; height:3px; position:absolute; top:0px; left:0px;">
		<a target="_blank" href="http://jigsaw.w3.org/css-validator/check/referer?profile=css3" style="color:#e51937; background:inherit;">.</a>
	</div>

</div>


<div id="middle_frame">
	
	<div id="wrapping"> 
		<!-- #BeginEditable "Body" -->	
	
<h1>arXiv Research</h1>

<div style="margin:0 auto; max-width:1000px;">

<h3>Research Description and Purpose of the Study</h3>
<p>
We invite you to participate in a research study about how people conduct searches in the arXiv database. This research will help us understand how people conduct searches and what may be most helpful in providing accurate information to users when they search this database. This research is being conducted by Paul Kantor, Principal Investigator, who is a Professor in the LIS Department of the School of Communication and Information at Rutgers University; 
<p>

<p>
This experimental interface is supported in part by the National Science Foundation, and hosted at Cornell University during development and testing. For information about the project visit:
 <a href="http://arxiv_xs.rutgers.edu/">http://arxiv_xs.rutgers.edu/</a>
</p>

<p><strong>
Please note that you must be at least 18 years of age to participate.
Please do not continue if you are not 18 or older.
</strong></p>	

<h3>Risks and benefits</h3>
<p>
This research may benefit you directly, if the experimental search is more effective, and it will help to better design search systems for users of databases, and the arXiv in particular. There are no foreseeable risks to participating in this research.
</p>

<h3>Protection of Privacy and Confidentiality of Data</h3>
<p>
We will do our best to protect your privacy as a research participant.  Because this information is being sent (transmitted) over the Internet, there is the possibility that an outside party (such as a computer hacker) may be able to gain access to your information.  If your search is being completed on a computer in a publicly accessible area, such as in a restaurant, at a school, a library, or at work, please understand that this can increase the possibility that others may be able to access your responses. Therefore, it is strongly advised that your search be completed on a private computer to protect your privacy. Please remember to always completely close your browser (or log-off) after completing your search.
</p>
 
<h3>Participation</h3>

<p>
You can participate in this research in two different ways (please
click the option you prefer in the appropriate box at the bottom of
the page after reading this entire form):

<ul>
<li>
A. You can participate completely anonymously by registering with a
username and password (and you may provide contact email needed for
password reset) and let us track your searches

<li>
 B. You can create a username and password, and share with us your
contact information for a possible follow-up interview, and let us
track all your searches (contact email will also support password
reset).
</ul>

<p>
The search you are conducting can be completely anonymous if you
choose option A. The search will be confidential if you choose option
B.  The software used to collect the data will not record the IP
address of your computer or any other identifying information (except
your username and password, and email if you chose to provide it).  We
will not ask questions related to your identity if you choose option
A.  Your responses will only be associated with your username and
password; we will only have the personal information you choose to
give us if you choose option B. At the end of your search we may ask
you for some feedback, giving us this feedback is optional.

<p>
We will track your search  data every time you search in the arXiv with your same username and password, until the end of the study in July 2016.

<p>
If you choose option B above, we will also be able to contact you for an interview.

<p>
The research team and the Institutional Review Board (a committee that
reviews research studies in order to protect research participants) at
Rutgers University are the only parties that will be allowed to see
the data, except as may be required by law. If a report of this study
is published, or the results are presented at a professional
conference, only group results will be stated. All study data will be
kept for 6 years.

<h3>Voluntary Participation</h3>

<p>
Your participation is entirely voluntary and you may choose not to
participate in this study or withdraw your consent at any time by
ending your search and logging out.  You will not be penalized in any
way should you choose not to participate or withdraw.


<h3>Contact Information</h3>

<p>We are available to answer your questions about this research. You
may contact the Principal Investigator:</p>

			<div style="width:180px; margin:10px auto; text-align:justify; background:#eeeeee; padding:10px; border-radius:10px;">
			<span style="font-weight:bold">Paul Kantor</span><br />
				Principal Investigator<br />
				(848) 932-8811<br />
				<a href="mailto:kantor@scils.rutgers.edu?subject=arXiv Research">paul.kantor@rutgers.edu</a><br />
				4 Huntington St., Room 310
				<br />New Brunswick, NJ 08901
			</div>

<p>For information about the project you can visit the project site: <a href="http://arxiv_xs.rutgers.edu/">http://arxiv_xs.rutgers.edu/</a></p>

<p>If you have any questions about your rights as a research subject, you may contact the IRB Administrator at Rutgers University:</p>

			<div style="max-width:550px; margin:10px auto; text-align:justify; background:#eeeeee; padding:10px; border-radius:10px;">
				<span style="font-weight:bold">Rutgers University Institutional Review Board for the Protection of Human Subjects</span><br />
				Office of Research and Sponsored Programs<br />
				3 Rutgers Plaza, New Brunswick, NJ 08901-8559<br />
				(848) 932-4058<br />
				humansubjects@orsp.rutgers.edu
			</div>

<p><em>
This Internet Research Information Sheet was approved by the Rutgers University Institutional Review Board for the Protection of Human Subjects on 6/14/2011; there is not any expiration on this approval.</em></p>

<p>
By participating in this study you agree to be a study subject. Please
click <strong>"YES"</strong> to one of the following options to allow tracking of your
search:
</p>

<p>By clicking on either of the options below I understand that completely anonymized versions of the data generated by my participation will be archived and may be made available to other researchers from Rutgers University of other institutions.
</p>
<script type="text/javascript"> 
			$(document).ready(function(){
			  $("#participation_level_anonymous").click(function(){
			    $("#participation_anonymous").fadeIn(500);
		   	    $("#participation_loginbased").fadeOut(0);
			 });
			});

			$(document).ready(function(){
			  $("#participation_level_loginbased").click(function(){
			    $("#participation_anonymous").fadeOut(0);
		   	    $("#participation_loginbased").fadeIn(500);
			 });
			});
</script>

	<div style="width:800px; height:85px; margin:0px auto; position:relative;">
					<div style="width:380px; height:60px; margin:none; padding:10px; float:left; text-align:justify; vertical-align:middle;">
		

						<div id="participation_level_anonymous">
							<input id="anonymous" type="radio" class="input_radio" value="anonymous" name="participation_level" tabindex="5" />
							<label for="anonymous" id="label_anonymous">A. Yes, I want to participate anonymously</label>
						</div>
		
			
						<div id="participation_level_loginbased">
							<input id="loginbased" class="input_radio" type="radio" value="login-based" name="participation_level" tabindex="7" />
							<label for="loginbased" id="label_loginbased">B. Yes, you can track all of my searches, in confidence</label>
						</div>	
					</div>

		<script type="text/javascript"> 
		$(document).ready(function(){
		$("#participation_level_anonymous").hover(function() { $("#expl_anonymous").fadeIn(100)}, function() { $("#expl_anonymous").fadeOut(100); });
	        $("#participation_level_loginbased").hover(function() { $("#expl_loginbased").fadeIn(100)}, function() { $("#expl_loginbased").fadeOut(100); });
		});

		$(document).ready(function(){
		$("#participation_level_anonymous").click(function(){
		$("#participation_anonymous").fadeIn(500);
		$("#participation_loginbased").fadeOut(500);
		 });
		});

		$(document).ready(function(){
		    $("#participation_level_loginbased").click(function(){
		    $("#participation_anonymous").fadeOut(500);
		    $("#participation_loginbased").fadeIn(500);
		 });
		});
		</script>

			<div style="width:400px; position:absolute; top:5px; right:0px; height:60px; padding:10px 0px 0px 0px; margin:none; float:right; vertical-align:middle; display:none; border:1px gray ridge; border-radius:10px;" id="expl_anonymous">


You can participate completely anonymously by registering with a username and password (and you may provide contact email needed for password reset) and let us track your searches without linking them to your contact information</div>
			
			<div style="width:400px; position:absolute; top:5px; right:0px; height:60px; padding:10px 0px 0px 0px; margin:none; float:right; vertical-align:middle; display:none; border:1px gray ridge; border-radius:10px;" id="expl_loginbased">You can create a username and password, and share with us your contact information for a possible follow-up interview, and let us track all your searches (contact email will also support password reset)</div>
	</div>


	<div style="height:15px; margin:10px auto 25px auto; text-align:center;">

		<div class="button_div" style="margin:0 auto; width:320px; height:15px; display:none;" id="participation_anonymous"><a href="registration.jsp?survey=false&code=<%=main.code%>" target="_self">Continue with Anonymous Participation</a></div>			
		<div class="button_div" style="margin:0 auto; width:320px; height:15px; display:none;" id="participation_loginbased"><a href="registration.jsp?survey=true&code=<%=main.code%>" target="_self">Continue with Login-based Confidential Participation</a></div>

	</div>


<p>
If you do not agree with the consent form and do not wish to participate in this study, please close this webpage on your web browser to exit from participating.
</P>

<p>
Thank you, Paul B. Kantor (Principal Investigator)
</p>

<%}%>
</body>
</html>
