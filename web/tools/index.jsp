<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% ResultsBase main=new ResultsBase(request,response); %>
<html>
<head>
<title>My.arXiv data research main page</title>
</head>
<body>
<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>

<h1>My.arXiv data research main page</h1>

<!--
<h2>Respondent Management Center</h2>

<ul>

<li><A href="tools/addRespondentForm.jsp">Add a new respondent entry</a>

<li><A href="tools/listRespondents.jsp">List all respondents</a>. You can also use this screen to access individual entries and modify them (e.g., set the "consent" flag, or correct the phone number). This page also shows the highest consent_id value in existence.

</ul>
-->

<h2>User Management Center</h2>

<ul>
<li><A href="../personal/editUserFormSelf.jsp">Edit your own user record</a>
<li><A href="listUsers.jsp">List all users</a> (including links to their interaction histories)
<li><A href="../admin/manageUsers.jsp">Manage users</a>
</ul>


<h2>Data Research Center</h2>
<ul>
<!--
<li><A href="tools/listRespondentsCsv.jsp">List all respondents</a> as a CSV file
-->
<li><A href="queryForm.jsp">Send a free-form SQL or JPQL query</a>
<!--
<li><A href="tools/responseSummaryQueryForm.jsp">SQL query + one-line summary of responses</a>
-->
</ul>


<!--
<h2>Documentation Center</h2>

<ul>

<li><A href="manual.html">User's Guide</a> - for interviewers and data researchers

<li><A href="tools/listRules.jsp">The transition rules for all survey pages</a>: summary of all "skips"; names of fields where inputs are recorded

<li><A href="deployment.html">Deployment Guide</a> - for maintenance programmers

<li><A href="javadoc/">API documentation</a>
-->

</ul>

<icd:RU/>

<% } %>

</body>
</html>