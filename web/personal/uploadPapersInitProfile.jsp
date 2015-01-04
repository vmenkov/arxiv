<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<%
   UploadPapersInitProfile main=new UploadPapersInitProfile(request,response);
%>
<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<% if (main.wantReload) { %>
<meta http-equiv="refresh" content="2; url=<%=main.reloadURL%>"/>
<%}%>
<title>Processing personal papers</title>
</head>
<body>

<% 
   if (main.error || main.status == UploadPapersInitProfile.Status.DONE_ERROR) {   %> 
 <%@include file="../include/error.jsp" %>
<%  } else if (main.status == UploadPapersInitProfile.Status.NONE) {  %>

<h1><%= main.checkTitle %></h1>
<pre>
<%= main.checkText %>
</pre>

<p>
You can go to the main <a href="uploadPapers.jsp">Document upload page</a> to upload more papers, or to the <a href="../index.jsp">My.ArXiv main page</a> to view your current recommendation list, if any.
</p>

<%  } else if (main.status == UploadPapersInitProfile.Status.DONE_NEED_APPROVAL) {  %>

<h1>Additional categories of interest</h1>

<p>Based on the content of the papers you have uploaded, we suggest that you add the following categories to the list of your categories of interest. If you don't want to add some or all of these categories, please un-check the boxes next to them.
</p>

<form method="post" action="uploadPapersInitProfile.jsp">
<input type="hidden" name="stageTwo" value="true">
<p><%= main.catBoxes %>
</p>

<!-- p><%= main.newCatReport %></p -->

<form method="post" action="uploadPapersInitProfile.jsp">
<input type="hidden" name="stageTwo" value="true">
<input type="submit" value="Continue with the checked categories">
</form>


<%  } else if (main.status == UploadPapersInitProfile.Status.RUNNING) {   %>

<h1><%= main.checkTitle %></h1>
<%= main.checkProgressIndicator %>
<pre>
<%= main.checkText %>
</pre>
<hr>
<p>
<% if (main.wantReload) { %>
This page should automatically refresh within a few seconds. 
If it does not, click the button below to continue:
<%}%>
</p>

<!-- using 'onclik' in order to pass the query string -->
<form>
<input onClick="location.href='<%=main.reloadURL%>'" type=button value="Continue">
</form>
     
<%   } else if (main.status == UploadPapersInitProfile.Status.DONE_ALL) {    %>

<h1>Processing personal papers</h1>

<p>Your personalized recommendations have been generated. 
Please go to the <strong><a href="../index.jsp">My.ArXiv Main page</a></strong> to view them.</p>


<%   }  else {    %>
<h1>Not sure what goes on!</h1>
<p>Status = <%= main.status %> </p>
<%   }    %>



<!--
<ul>
<li><a href="<%=main.cp%>">Main page</a>
<li><a href="<%=main.cp%>/personal/index.jsp">Your account</a>
<li><a href="<%=main.cp%>/personal/editUserFormSelf.jsp">Modify your account information</a>
</ul>
-->

<icd:RU/>

</body>
</html>
