<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>

<% 
   GetUser main=new GetUser(request,response);

   if (main.error) {   %>  <%@include file="../include/error.jsp" %>

<p>
To try again, go back to the <a href="manageUsers.jsp">User Management</a>
main page.
</p>

<%   } else {      
   String id = main.u.getUser_name();
%>

<html>
<head>
<title>Editing a user's record
</title>
<jsp:include page="../include/date-head-include.html" />
</head>
<body>

<h1>Editing the entry for user <em><%=id%></em></h1>

<p>Experiment plan: <%= main.u.getProgram() %></P>

<p>
<table border=1>
<tr><%= main.u.header4cells()  %> </tr>
<tr><%= main.u.to4cells()  %> </tr>
</table>
</p>

<form method=post action="editUser.jsp">
<%= Tools.inputHidden(EditUser.USER_NAME, id) %> <br>

<h3>Updating user account information</h3>

<p>
<icd:UserEntryForm user_name="<%=id%>"/>

<% if (main.u.getProgram()==User.Program.EE4) { 
%>
<h3>Recommendation generation preferences</h3>
	<table>
	<tr>
			<td style="width:50%; text-align:right; vertical-align:middle;"><label for="days">Selectivity
</label></td>
			<td style="width:50%; text-align:left; vertical-align:middle;">
<%= main.ee4form() %>
</td>
		</tr>
	</table>

<%}%>


<h3>Updating roles</h3>

<%= main.mkRoleBoxes() %>

<h3>Changing password and status</h3>

<ul>
<li>To change password for an already enabled user, simply type (and re-type) the new password.
<li>To enable login for a previously disabled user, type (and re-type) the new password for the user, and check the "enabled" button.
<li>To disable login for the user, check the "disabled" button. The password field will be ignored.
<li>To make no changes, don't type anything in the password box, and don't touch the radio buttons.
</ul>

<p>
<%= main.mkRadioSet() %>
</p>
<p>
<%= EditUser.pwTable() %>
</p>

<p>
<input type="submit" value="Update user record">
</p>

<h3>Updating interest areas</h3>
<%= main.u.mkCatBoxes() %>
</p>
<p>
<input type="submit" value="Update user record">
</form>
</p>

<% } %>

<icd:RU/>

</body>
</html>