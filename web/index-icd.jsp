<%@ page import="java.io.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<html>
<head>
<title>ICD research application main page</title>
</head>
<body>

<h1>ICD research application main page</h1>

<h2>Respondent Management Center</h2>

<ul>

<li><A href="tools/addRespondentForm.jsp">Add a new respondent entry</a>

<li><A href="tools/listRespondents.jsp">List all respondents</a>. You can also use this screen to access individual entries and modify them (e.g., set the "consent" flag, or correct the phone number). This page also shows the highest consent_id value in existence.

</ul>

<h2>Interviewing Center</h2>

<ul>

<li><A href="tools/showReservations.jsp">Show reservations</a>: List all
respondents already reserved by an interviewer for today. You can then proceed to interview any of them.

<li><A href="tools/findEligible.jsp">List eligible respondents</a>: List the
respondents that you have already reserved, as well as others that you
can reserve to interview today</a> 

</ul>

<h2>User Management Center</h2>

<ul>

<!--
<li><A href="tools/addRespondentForm.jsp">Add a new respondent entry</a>
-->

<li><A href="personal/editUserFormSelf.jsp">Edit your own user record</a>
<li><A href="tools/listUsers.jsp">List all users</a>
<li><A href="admin/manageUsers.jsp">Manage users</a>

</ul>



<h2>Data Research Center</h2>
<ul>
<li><A href="tools/listRespondentsCsv.jsp">List all respondents</a> as a CSV file
<li><A href="tools/queryForm.jsp">Send a free-form SQL query</a>
<li><A href="tools/responseSummaryQueryForm.jsp">SQL query + one-line summary of responses</a>
</ul>


<h2>Documentation Center</h2>

<ul>

<li><A href="manual.html">User's Guide</a> - for interviewers and data researchers

<li><A href="tools/listRules.jsp">The transition rules for all survey pages</a>: summary of all "skips"; names of fields where inputs are recorded

<li><A href="deployment.html">Deployment Guide</a> - for maintenance programmers

<li><A href="javadoc/">API documentation</a>


</ul>

<icd:RU/>

</body>
</html>