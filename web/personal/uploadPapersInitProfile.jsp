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
<jsp:include page="../include/date-head-include.html" />
</head>
<body>

<% 
   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else if (main.check) {      %>
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
     
<%   } else {    %>

<h1>Processing personal papers</h1>


<p>Your personalized recommendations have been generated. Please go to the main page to view them.</p>

<p>Go to the "<a href="../index.jsp">Main page</a>


<%   }      %>


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
