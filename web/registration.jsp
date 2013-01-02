<%@page contentType="text/html; charset=UTF-8" %> 
<!-- © 2011 by AEP -->
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ page import="edu.rutgers.axs.Version" %>
<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
    Participation  main=new Participation (request,response);
    boolean survey = main.getBoolean(EditUser.SURVEY, false);
  
	String spacer="	<tr>"+
			"<td style=\"width:50%; text-align:right; vertical-align:middle; height:5px; border:none;\">&nbsp;</td>" +
			"<td style=\"width:50%; text-align:left; vertical-align:middle; height:5px; border:none;\">&nbsp;</td>" +
		"</tr>";

%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<!-- #BeginTemplate "_technical/templates/Template.dwt" -->

<head>
<!-- #BeginEditable "doctitle" -->
<title>arXiv - Participation Page - User registration</title>
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
<!--
function BlurLinks(){
lnks=document.getElementsByTagName('a');
for(i=0;i<lnks.length;i++){
lnks[i].onfocus=new Function("if(this.blur)this.blur()");
}
}
//-->
</script>

<!-- #BeginEditable "Styles" -->


<script src="_technical/scripts/jquery.validate.js" type="text/javascript"></script>
<script src="_technical/scripts/additional-methods.js" type="text/javascript"></script>

<style type="text/css">
#signupform { /*width: 670px;*/ }
#signupform label.error {
	margin:0px 0px 0px 2px;
	width: auto;
	display:block;
	position:relative;
	border:none;
	color:red;
}
</style>


<!-- commenting out the script, in order to actually get things done -->
<!-- script type="text/javascript" src="scripts/participation_login.js"></script-->

<style type="text/css">

#left_frame {width:192px;}

#right_frame
{
left:202px;
padding-top:0px;
padding-bottom:0px;
}

.button
{width:170px}

.address
{width:260px; margin:0px auto;}

.double_address
{width:530px; margin:0px auto;}

h2
{margin-top:10px;}

.adjust_height
{min-height:100%;}

.duties_1
{margin: 0 0 0 50px; text-indent:-50px;}

.duties
{margin: 0 0 0 50px; text-indent:-8px;}

.icon
{
width:16px;
height:16px;
margin:2px 6px -2px 4px;
border:0px;
}

</style>

<!--[if IE]>

<style type="text/css">

#left_frame
{
width:195px;
}

.button
{width:175px}

#right_frame
{
left:202px;
width:expression((document.body.clientWidth -221) + "px");
}

.adjust_height
{height:expression((document.getElementById('Right_Frame').offsetHeight - 47) + "px");}

</style>

<![endif]-->

<script type="text/javascript">

function StartScripts() { 
BlurLinks();
}

window.onload = StartScripts;
</script>


<!-- #EndEditable -->

</head>

<body>
<!--
<script type="text/javascript">function Gomenu(){return}</script>
<script type="text/javascript" src="../menu/menu_var.js"></script>
<script type="text/javascript" src="../menu/menu_com.js"></script>
<noscript>Your browser does not support scripts in our web site.</noscript>
-->

<div id="upper_frame">

	<!-- <div id="MenuPos" style="position:absolute; left: 86px; top: 42px; z-index:5000;"></div> -->
	<img src="_technical/images/bar-rutgers.jpg" style="border:none; width:1000px; height:75px; position:absolute; top:0px; left:0px;" alt="" />
	<div style="position:absolute; top:10px; left:10px; font-size:4em; font-weight:bold; text-shadow: 2px 2px 2px #000; right: 813px;"><span style="color:#CCCCCC; font-weight:normal;">my:</span>arXiv</div>
	
	<div id="layer_page_title" style="z-index: 10">
		<!-- #BeginEditable "Page_Title" -->

	</div>
	
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
	
	
<h1>New user registration</h1>

<div style="text-align:center;">

Thank you for choosing to participate in the arXiv research at the confidential login-based level. Please create your account.<br />

<!--
<small>(At some point we may add here a feature to import one's account info from arxiv.org...)</small><br /><br />
-->


<form class="cmxform" id="signupform" method="post" 
      action="newUserSelf.jsp" 
      style="text-align:center;">

<%= Tools.inputHidden(EntryFormTag.PREFIX + EditUser.SURVEY, survey) %>

	<table style="width: 100%;" cellspacing="0" cellpadding="0">
<tr><td colspan="2"><h3>Registration code</h3></td></tr>
	<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="username">Registration code</label>
<br><small>(If your invitation told you to enter a registration code, enter it here; otherwise, leave the field as it was)</small>
</td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="code" name="code" value="<%=main.code%>" style="width:150px" /></td>
		</tr>

<tr><td colspan="2"><h3>Choose your user name and password</h3></td></tr>
		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="username">Desired Username</label>
<br><small>(Up to 15 characters long)</small>
</td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="username" name="user_name" style="width:150px" /></td>
		</tr>
<%=spacer%>

		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="password">Desired Password</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="password" name="password" type="password" style="width:150px" /></td>
		</tr>
		
		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="confirm_password">Retype Password</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="confirm_password" name="confirm_password" style="width:150px" type="password" /></td>
		</tr>

<%=spacer%>

<% if (!survey) { %>
<tr><td colspan="2"><h3>Personal information (optional)</h3></td></tr>


		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="email">E-mail Address (For resending the login information if you forget it.)</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="email" name="r.email" style="width:150px" /></td>
		</tr>

		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="confirm_email">Retype E-mail Address</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="confirm_email" name="confirm_email" style="width:150px" /></td>
		</tr>

<%=spacer%>

		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="firstName">First and middle name (optional)</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="firstName" name="r.firstName" style="width:150px" /></td>
		</tr>

		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="firstName">Last name (optional)</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="lastName" name="r.lastName" style="width:150px" /></td>
		</tr>


<% } else  { %>

<tr><td colspan="2"><h3>Optional follow-up telephone interview</h3>

<p> We are conducting a few follow-up interviews with searchers to get
more detailed information about how to improve the arXiv system.

<p> If you are willing also to be interviewed, you can give us your
name, telephone number or email, for a follow-up telephone interview
some time in the future.  The optional follow-up telephone interview
is confidential, not anonymous. The telephone interview contains
questions that you may find of a personal nature such as demographic
information.  One potential risk of participating in the follow-up
study is that confidential information about you may be accidentally
disclosed outside of the research team.  We will use our best efforts
to keep the information you provide secure, and we think the risk of
accidental disclosure is very small.  A link between your personal
information, such as name, phone number and demographic information,
and the data we collect will be kept to allow for follow-up to
complete the interview and to mail the payment for completing the
interview.

<p> However, after we have processed your telephone interview we will
sever the link between your personal information and the data you gave
us. We would like to audio tape the telephone interview with you so
that we can review it, and make notes. As soon as we process your
telephone interview, we will destroy the audio record itself, keeping
only the anonymous notes, audio taping your interview renders the data
collection confidential not anonymous.

<p> We will pay you US<strong>$50</strong>, for a completed
interview. If you decide to stop partway through an interview you will
be paid for the fraction of the interview that has been completed.

<p> We hope you can give us about 20 minutes of your time to help our
study. We may do the telephone interview in one long call, or in two
shorter ones in the same week if you prefer. There are no foreseeable
risks to participating in this research.

<p> Your participation in our study is completely voluntary, and
refusing to participate will involve no penalty. You may stop the
interview at any time without penalty.

<p> We look forward to talking with you about your unique experience
with searches in the arXiv.

<p> By giving you my contact information I agree to participate in the
telephone interview portion of the study.

</td></tr>
		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="firstName">First and middle name</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="firstName" name="r.firstName" style="width:150px" /></td>
		</tr>

		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="firstName">Last name </label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="lastName" name="r.lastName" style="width:150px" /></td>
		</tr>

<%=spacer%>

		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="email">E-mail Address (Can also be used for resending the login information if you forget it.)</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="email" name="r.email" style="width:150px" /></td>
		</tr>

		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="confirm_email">Retype E-mail Address</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="confirm_email" name="confirm_email" style="width:150px" /></td>
		</tr>

<%=spacer%>

		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="phoneNumber">Telephone Number</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="phoneNumber" name="r.phoneNumber" style="width:150px" /></td>
		</tr>


		<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="timeToReach">Best Times to Reach You: day(s) of week, time(s) of day</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="timeToReach" name="r.timeToReach" style="width:150px" /></td>
		</tr>


<tr><td colspan=2 align=left><strong> By clicking on the box below you approve
the use of audio recording for the telephone interview</strong>
</td></tr>

	<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="approvedAudio">Click here to approve audio recording</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><%= Tools.checkbox(EntryFormTag.PREFIX + "approvedAudio", "on", null, false) %></td>
	</tr>

<%}  %>
	</table>

<% if (main.program==User.Program.SET_BASED) { %>
<h3>Recommendation generation preferences</h3>
	<table>
	<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="days">Recommend recent articles no older than so many days. The value can be from 1 to 30; default 7
</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;"><input id="days" name="r.days" value="<%=Search.DEFAULT_DAYS%>" style="width:150px" /></td>
		</tr>
	</table>
<% }
 if (main.program==User.Program.EE4) { 
%>

<h3>Recommendation preferences</h3>
<p>
The selectivity of the recommendation system may be set here, or also
altered later.  Setting the selectivity higher, e.g., to Selective (at
least 1 in 2 papers shown should be interesting), causes the system to
more selective, eliminating potentially uninteresting papers more
aggressively at the risk of missing some interesting ones.  Setting
the selectivity lower, e.g., to Inclusive (at least 1 in 32 papers
shown should be interesting), causes the system to be less selective,
and to show more papers, potentially uncovering more interesting
papers but also showing more uninteresting ones.  We suggest a default
of 1 in 8.
</p>
<p>
	<table>
	<tr>
		<td style="width:50%; text-align:right; vertical-align:middle;"><label for="days">Selectivity
</label></td>
		<td style="width:50%; text-align:left; vertical-align:middle;">
<%= main.ee4form() %>
</td>
		</tr>
	</table>
</P>
<%
}
if (!main.bernoulli) { %>
<h3>Your interest areas</h3>
	

<p>You need to specify at least one interest area ("subject category")
in order for My.ArXiv to be able to generate recommendations for
you. You can specify as many interest areas as you want. You will be
able to change the list of interest areas at any time.  </P>

<p>If you want to see what kind of articles are categorized under each
subject category, please see the main page of <a
href="http://arxiv.org/">ArXiv.org</a>.  </P>

<p>
<%= Categories.mkCatBoxes(null) %>
</p>
<% } %>
	<input type="submit" value="Register" id="submit" /> 	
	
</form>

</div>

	
	
		<!-- #EndEditable -->	
	</div> <!-- Wrapping -->
	
	</div> <!-- Middle frame ends-->

<!-- div id="lower_frame">
	
</div --> <!-- Lower frame ends -->		

<!-- #BeginEditable "Scripts" -->
<!-- #EndEditable -->

</body>

<!-- #EndTemplate -->

</html>
