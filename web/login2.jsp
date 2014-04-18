<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.Version" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<head>
<link rel="icon" type="image/x-icon" href="favicon.ico" />
<title>My.arXiv - User Authentication Page</title>
</head>

<body>
<h1>My.arXiv - User Authentication Page (ver. <%=Version.getVersion()%>)</h1>

<form method="POST" action="LoginServlet">
      <input type="hidden" name="sp" value="<%=request.getParameter("sp")%>">
      <input type="hidden" name="qs" value="<%=request.getParameter("qs")%>">
<table>
	<tr>
		<td colspan="2">
To use personalized browsing features on my.arxiv.org, 
please login with your user name and password:
</td>
	</tr>
	<tr>
		<td>Name:</td>
		<td><input type="text" name="j_username" ></td>
	</tr>
	<tr>
		<td>Password:</td>
		<td><input type="password" name="j_password" ></td>
	</tr>
	<tr>
	<td colspan="2">
	<input type="checkbox" name="remember" value="yes"> 
	Remember me, for up to <%=ExtendedSessionManagement.maxDays%> days.
	<small>(Don't check this if you are using a public/shared computer)</small>
	</td>
	</tr>
	<tr>
		<td colspan="2"><input type="submit" value="Log in" ></td>
	</tr>
</table>

<p>Forgot your password? You can <a href="resetPasswordForm.jsp">request password reset.</a>

<p>Not a subscriber? <a href="participation.jsp">Join the project now!</a>
</p>

</form>
</body>
</html>
