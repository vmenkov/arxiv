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

<h1>Record updated</h1>

<p>Account created for User_name = <em><%=id%></em>:</p>

<p>
<table border=1>
<tr><%= u.header4cells()  %> </tr>
<tr><%= u.to4cells()  %> </tr>
</table>
</p>

<p>
Now you can 
<a href="<%=main.cp%>/personal/logged-in.jsp">Log in with your new user name and password</a> to continue browsing.
</p>

<p>
<ul>
<li><a href="<%=main.cp%>">Main page</a>
<li><a href="<%=main.cp%>/personal/index.jsp">Your account</a>
<li><a href="<%=main.cp%>/personal/editUserFormSelf.jsp">Modify your account information</a>
</ul>


<%   }      %>

<icd:RU/>

</body>
</html>
