<%@ page import="edu.rutgers.axs.web.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ResultsBase main=new ResultsBase(request,null);
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>My.arXiv - User Authentication Page</title>
</head>

<body>
<h1>My.arXiv - User Authentication Page</h1>


<form method="POST" action="j_security_check">
<table>
	<tr>
		<td colspan="2">
To use personalized browsing on my.arxiv.org, please login with your user name and password:
</td>
	</tr>
	<tr>
		<td>Name:</td>
		<td><input type="text" name="j_username" /></td>
	</tr>
	<tr>
		<td>Password:</td>
		<td><input type="password" name="j_password"/ ></td>
	</tr>
	<tr>
		<td colspan="2"><input type="submit" value="Log in" /></td>
	</tr>
</table>

<p>Not a subscriber? <a href="<%=main.cp%>/participation.html">Join the project now!</a>
</p>

</form>
</body>
</html>
