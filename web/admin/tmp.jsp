<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   GetUser main=new GetUser(request,response);
%>
<html>
<head>
<title>User Management</title>
</head>
<body>

<h1>User Management</h1>
<h2>List of Users</h2>

<p>Listed below are all users of the system.</p>

<p>Passwords are shown in encrypted form. If no password shows, the
user entry is disabled.</p>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>
<p>
<table border=1>
<tr>
<th>
<th>User name
<th>Content
<th>Enabled?
<th>Edit
</tr>
<%	
	for(int i=0; i<main.list.size(); i++) {
	User u =  main.list.elementAt(i);
%><tr>
<td><%= i+1 %>
<td><%= u.getUser_name() %></td>
<td><%= u.reflectToString() %></td>
<td><%= u.isEnabled()? "Yes" : "No" %></td>
<td><a href="editUserForm.jsp?id=<%= u.getUser_name() %>">Edit</a></td>
</tr>
<%  }  %>
</table></p>
<hr>
<p>Message: <%= main.infomsg%> </p>
<% }     %>

<h2>Add a user</h2>


<icd:RU/>

</body>
</html>