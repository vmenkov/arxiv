<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>

<html>
<head>
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Editing account information
</title>
<jsp:include page="../include/date-head-include.html" />
</head>
<body>

<% 
   EditUserForm main=new EditUserForm(request,response);
   String id = main.user;
   User u =  main.getUserEntry();
   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {      %>


<h1>Updating your account information and preferences</h1>

<p>User_name = <em><%=id%></em></p>

<p>
<table border=1>
<tr><%= User.header4cells()  %> </tr>
<tr><%= u.to4cells()  %> </tr>
</table>
</p>

<form method=post action="editUserSelf.jsp">
<%= Tools.inputHidden(EditUser.USER_NAME, main.user) %> <br>

<h3>Updating your account information</h3>

<p>All information in this section is optional. 

<p>If you supply a correct email address, it can be used to reset a
lost password if necessary.

<p>Other contact information only needs to be supplied if you have
agreed to participate in the follow-up interview; please see the relevant
<a href="../interview-information-sheet.html">information sheet</a>.

<p><icd:UserEntryForm user_name="<%=id%>"/>

<% if (u.getProgram()==User.Program.EE4) { 
%>
<h3>Recommendation preferences</h3>

<p> Here you can adjust the selectivity of the recommendation
system. Setting the selectivity higher, e.g., to <tt>Selective (at least 1
in 2 papers shown should be interesting)</tt>, causes the system to more
selective, eliminating potentially uninteresting papers more
aggressively at the risk of missing some interesting ones.  Setting
the selectivity lower, e.g., to <tt>Inclusive (at least 1 in 32 papers
shown should be interesting)</tt>, causes the system to be less selective,
and to show more papers, potentially uncovering more interesting
papers but also showing more uninteresting ones.  We suggest a default
of <tt>1 in 8</tt>.  </p>


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

<h3>Changing password and status</h3>

<p>If you want to change your password, enter the new passowrd below (same word in both boxes). Otherwise just leave the boxes empty.</p>

<p>
<%= EditUser.pwTable() %>
</p>

<input type="submit" value="Update account information">

<% if (!u.getProgram().needBernoulli()) { %>
<h3>Updating interest areas</h3>

<p>You need to specify at least one interest area in order for
My.ArXiv to be able to generate recommendations for you.
<%= u.mkCatBoxes() %></p>

<p><input type="submit" value="Update account information"></p>
<% } %>

</form>
<%   }      %>

<icd:RU/>

</body>
</html>
