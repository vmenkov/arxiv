<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ListUsers main=new ListUsers(request,response);
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

<!-- tr>
<th>
<th>User name
<th>Details
<th>Roles
<th>Enabled?
<th>Edit
</tr -->

<tr>
<th>row no.</th>
<%= User.header4cells()  %> 
<th>Edit user record
</tr>
<%	
	for(int i=0; i<main.list.size(); i++) {
	User u =  main.list.elementAt(i);
%>
<tr>
<td><%=i+1%></td>
<%= u.to4cells()  %>
<td><a href="editUserForm.jsp?user_name=<%= u.getUser_name() %>">Edit</a></td>
</tr>
<%  }  %>
</table></p>
<hr>
<p><small>Message: <%= main.infomsg%> </small></p>
<% }     %>

<h2>Add a new user</h2>

<form action="editUserForm.jsp">
<%= Tools.inputHidden(GetUser.CREATE, "true") %> 
<p>New user name: 
<%= Tools.inputText(EditUser.USER_NAME) %> 
<input type=submit value="Continue">
</p>
</form>

<icd:RU/>

</body>
</html>