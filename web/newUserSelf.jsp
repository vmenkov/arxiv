<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<%
   //request.getSession().invalidate(); // forget any old login name
   //request.logout();
   EditUser main=new EditUser(request,response, EditUser.Mode.CREATE_SELF);
   String id = main.uname;
   User u =  main.r;
%>
<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>Creating new user entry
</title>
<jsp:include page="include/date-head-include.html" />
</head>
<body>

<% 
   if (main.error) {   %>  <%@include file="include/error.jsp" %>

<p>Use the "back" button of your browser to get back to your data entry page.</p>


<%   } else {      %>

<h1>Account created</h1>

<p>Account created for User_name = <em><%=id%></em></p>

<!-- <p>
<table border=1>
<tr><%= u.header4cells()  %> </tr>
<tr><%= u.to4cells()  %> </tr>
</table>
</p>  -->

<% 
   if (main.needTorontoLink) {   %>  

<p>
<form action="<%=main.cp%>/login2.jsp">
To help My.ArXiv find articles of interest to you, you can upload a few documents that you know <strong>are</strong> interesting. Those can be papers you have read on ArXiv.org or elsewhere, or even some papers you wrote yourself.
<input type="hidden" name="sp" value="/personal/uploadPapers.jsp">
<input type="submit" value="I want to upload some papers">
</form>
</p>

<p>
<form action="<%=main.cp%>/login2.jsp">
<input type="hidden" name="sp" value="/index.jsp">
</form>
Or 
<input type="submit" value="Continue without uploading papers">
</form>
</p>

<p>In either case, you will be asked for your new login name and password.</p>

<%   }  else {   %>

<p>
Click on the button below to log in with your new user name and password to continue browsing.
<form action="<%=main.cp%>/login2.jsp">
<input type="hidden" name="sp" value="/index.jsp">
<input type="submit" value="Continue">
</form>
</p>

<!-- a href="<%=main.cp%>/login2.jsp?sp=/index.jsp">Log in with your new user name and password</a -->

<%   }   %>


<!-- p>
<ul>
<li><a href="<%=main.cp%>">Main page</a>
<li><a href="<%=main.cp%>/personal/index.jsp">Your account</a>
<li><a href="<%=main.cp%>/personal/editUserFormSelf.jsp">Modify your account information</a>
</ul>
</p -->

<%   }      %>

<icd:RU/>

</body>
</html>
