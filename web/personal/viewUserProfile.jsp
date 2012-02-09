<%@ page import="java.io.*" %>
<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ page import="edu.rutgers.axs.recommender.*" %>
<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
   ViewUserProfile main=new ViewUserProfile(request, response);
   User actor = main.actor;
%>

<html>
<head>
<meta name="Description" content="List of terms in the user profile, based on pages the user has been interested in"/>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta http-equiv="Content-Style-Type" content="text/css" />
<link rel="icon" type="image/x-icon" href="../favicon.ico" />
<title>Personal folder for user <%= main.actorUserName %>
</title>
<script type="text/javascript" src="../_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="../_technical/scripts/jquery-transitions.js"></script>
</head>
<body>

<h1>
<% if (main.requestedFile!=null) { %>
Profile <%=main.requestedFile%>
<%}else{%>
Most recent currently available profile 
<%}%>
for user
<em><%= main.actorUserName %></em>
</h1>

<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {

     if (!main.force && main.upro!=null) {      
       long since = main.actor.getLastActionId() -main.df.getLastActionId();
%>


<p>User profile <%=main.df.getThisFile() %> was generated for user  
<%=main.df.getUser() %> at: <%=Util.ago(main.df.getTime())%>. 

<p>This profile reflects <%= main.df.getLastActionId() %> operations recorded in the user's 
<a href="<%=main.viewActionsLink()%>">activity log</a>. There have been <%= since %> user activity operations recorded since.

<% if (main.requestedFile!=null) { %>

<p>This may or may not be the most recent profile. To see the most
recent profile, please <a
href="<%=main.viewLatestProfileLink()%>">click here</a>.

<% } else if (since>0) { %>

<p>(Is this too long ago? You can <a href="#tasks">update</a> the profile to account for your activity since this profile has been generated).
</p>

<% } else { %>

<p>(This profile appears to reflect all user actions recorded so far; nonetheless, if you really want to, you can probably request an <a href="#tasks">update</a> of the profile. This probably won't result in any visible changes to it.)
</p>

<% }  %>

<p>This profile contains <%=main.upro.terms.length%> terms. They are listed below in the descending order of f(t)*idf(t).
</p>

<p>
<table>
<tr>
<th>No.</th>
<th>Term <em>t</em></th>
<th>f(<em>t</em>)</th>
<th>f(SQR(<em>t</em>))</th>
<th>idf(<em>t</em>)</th>
</tr>

<% for(int i=0; i<main.upro.terms.length; i++) { 
String t=main.upro.terms[i];
UserProfile.TwoVal h= main.upro.hq.get(t); 
%>
<tr>
<td><%=i%></td>
<td><%=t%></td>
<td><%=h.w1%></td>
<td><%=h.w2%></td>
<td><%=main.upro.dfc.idf(t)%></td>
</tr>

<%}%>
</table>
</p>

<% } else if (main.requestedFile!=null)  { %>
<p>No profile file found with this name: <%=main.requestedFile%>
<% } else if (!main.force)  { %>
<P>No user profile for user <em><%= main.actorUserName %></em> has been generated yet. 
</p>
<% } %>

<%if (main.requestedFile==null) { %>
<h2><a name="tasks">(Re-)computing the profile</a></h2>

<%    if (main.activeTask!=null) {
%> 
<p>
Presently, a task is running to generate the user profile: <%= main.activeTask%>. You may wait for a couple minutes, and then reload this page to see if it has completed.</p>
<%      
      } else if (main.queuedTask!=null) {
%>
<p>
Presently, a task is queued to generate the user profile: <%= main.queuedTask%>. You may wait for a few minutes, and then refresh this page to see if it has started and completed.</p>
 <%
       } else if (main.newTask!=null) {
%>
<p>
A new task to generate the user profile has just been created and queued: <%= main.newTask%>. You may wait for a couple minutes, and then reload this page to see if it has started and completed.</p>
 <%
     }   else {
%>
Presently, no task has been scheduled to (re-)generate the user profile. If desired, you can request it now.
</p>
 <%      
      }
%>

<% if (main.newTask==null) { %>
<p>
Due to the computational costs, user profiles are created in an off-line mode. 
To create a new computational task to <%= (main.df==null) ? "create":"update" %>
the user profile for <em><%= main.actorUserName %></em>, click on the button below:
<form action="viewUserProfile.jsp">
<input type="hidden" name="<%=main.USER_NAME%>" value="<%=main.actorUserName%>">
<input type="hidden" name="<%=main.FORCE%>" value="true">
<input type="submit" value="Create task">
</form>
</p>
<%  }  
}%>

<hr>
<p>
<form action="viewUserProfile.jsp">
<input type="hidden" name="<%=main.USER_NAME%>" value="<%=main.actorUserName%>">
<input type="submit" value="Refresh view">
</form>
</p>



<%  }  %>


<hr> <p><small>System message: <%= main.infomsg%> </small></p> <%  %>

<icd:RU/>

</body>
</html>