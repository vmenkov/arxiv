<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<%
%>
<html>
<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>Lost your password?
</title>
<jsp:include page="include/date-head-include.html" />
</head>
<body>

<%   {      %>

<h1>Forgot your password</h1>

<p>If you have forgotten your password, you can request our server to reset it. To do it, you need to know you account's user name, and the email address associated with it. (You were given an option to provide the email address during registration). 

<form method="POST" action="resetPassword.jsp">
<table>
	<tr>
		<td colspan="2">
</td>
	</tr>
	<tr>
		<td>User name:</td>
		<td><input type="text" name="user_name" ></td>
	</tr>
	<tr>
		<td>Email address:</td>
		<td><input type="text" name="email" ></td>
	</tr>
	<tr>
		<td colspan="2"><input type="submit" value="Request password reset" ></td>
	</tr>
</table>

<%   }      %>

<icd:RU/>

</body>
</html>
