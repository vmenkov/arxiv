<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% ResultsBase main=new ResultsBase(request,response); %>
<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<link rel="stylesheet" type="text/css" href="_technical/styles/styles_all.css" />
<title>My.arXiv data research main page</title>
</head>
<body>
<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>

<h1>My.arXiv data research main page</h1>


<h2>User Management Center</h2>

<ul>
<li><A href="../personal/editUserFormSelf.jsp">Edit your own user record</a>
<li><A href="listUsers.jsp">List all users</a> (including links to their interaction histories)
<li><A href="../admin/manageUsers.jsp">Manage users</a>
<li><A href="../admin/invitations.jsp">Manage invitations</a>
<li>Send a notification email to yourself or another user. Pressing the "SEND" button will send the current recommendation list to the user right away, much in the same way it's done by the regular nightly email script. 
<form action="emailSug.jsp" method="post"><ul>
<li>My.ArXiv user name (<em>not</em> the email address) <input type="text" name="user_name" value="<%=main.user%>" size="20">
<li>Check this box to <strong>force</strong> sending email (disregarding the user's opting out, or the scheduling rules): <input type="checkbox" name="force" value="true">
<li><input type="submit" value="Send email">
</ul></form>
</ul>


<h2>Algorithms center</h2>
<ul>
<li><A href="internals.jsp">My.ArXiv algortithm internals</a>: view all kinds of user profiles and suggestion lists used now, or potentially, by My.ArXiv. These are various things that we mostly don't expose to the end user.
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


<h2>Documentation Center</h2>

<ul>

<li><A href="../doc/html/api/">API documentation</a> - for developers / maintenance programmers, or those who want to reuse some of my.arxiv.code

<li><A href="install.html">Deployment Guide</a> - for developers / maintenance programmers and system administrators

<li><A href="setup.html">Setting up your environment</a> - for developers / maintenance programmers 

<li><A href="svd.html">SVD + k-Means clustering</a>

<li><A href="../javadoc/">API documentation</a>

<!--
<li><A href="manual.html">User's Guide</a> - for interviewers and data researchers

-->

</ul>

<icd:RU/>

<% } %>

</body>
</html>
