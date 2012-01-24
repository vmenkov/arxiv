<%@ page import="java.io.*" %>
<%@ page import="java.util.*" %>

<%@ page import="edu.rutgers.axs.web.*" %>
<%@ page import="edu.rutgers.axs.sql.*" %>
<%@ page import="edu.rutgers.axs.recommender.*" %>
<%@ page import="edu.rutgers.axs.html.*" %>

<%@ taglib uri="http://my.arxiv.org/taglibs/icdtags" prefix="icd" %>
<% 
	ViewSuggestions main=new ViewSuggestions(request,response);
%>
<!-- © 2011 by AEP -->
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">

<!-- #BeginTemplate "_technical/templates/Template.dwt" -->

<head>
<!-- #BeginEditable "doctitle" -->
<title>My.arXiv - Search results</title>
<!-- #EndEditable -->
<meta name="Keywords" content="" />
<meta name="Description" content="Results of a search against the local copy of the arxiv.org database" />
<meta name="Author" content="pichugin@eden.rutgers.edu" />
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta http-equiv="Content-Style-Type" content="text/css" />

<link rel="stylesheet" type="text/css" href="_technical/styles/styles_all.css" />
<!--[if lte IE6]>
<link rel="stylesheet" type="text/css" href="_technical/styles/styles_ie.css" /><![endif]-->
<link rel="stylesheet" type="text/css" href="styles/results.css" />
<!--[if IE]>
<link rel="stylesheet" type="text/css" href="styles/results_ie.css" />
<![endif]-->
<link rel="icon" type="image/x-icon" href="favicon.ico" />

<script type="text/javascript" src="_technical/scripts/jquery.js"></script>
<script type="text/javascript" src="_technical/scripts/jquery-transitions.js"></script>
<script type="text/javascript" src="scripts/blur.js"></script>
<script type="text/javascript" src="scripts/buttons_control.js"></script>

<!-- #EndEditable -->

</head>

<body>


<%   if (main.error) {   %>  <%@include file="../include/error.jsp" %>
<%   } else {     

     if (main.entries!=null) {
 %>
	
		<div id="wrapping"> 
		<!-- #BeginEditable "Body" -->	
	
		<h1>Suggestions</h1>

<p>Suggestion liste <%=main.df.getThisFile() %> was generated for user
<%=main.df.getUser() %> at: <%=Util.ago(main.df.getTime())%>. (Is this
too long ago? You can <a href="#tasks">update</a> the profile to
account for your activity and new article submission since this
list has been generated).  </p>
		
		<% 
		int rowCnt=0;
for( ArticleEntry e: main.entries) { 
		  if (++rowCnt >= main.maxRows) break;
%>
		<div class="result" id="result<%=e.i%>">
			<div class="document">
			<%= e.i %>. [score=<%=e.score%>] <%= e.idline %> 
[<a href="<%=main.urlAbstract(e.id)%>">Abstract</a>]
[<a href="<%=main.urlPDF(e.id)%>">PDF/PS/etc</a>]
 <br />
			<%= e.titline %><br />
			<%= e.authline %><br />
			<% if (!e.commline.equals("")) { %>
			<%= e.commline %><br />  
			<%}%>
			<%= e.subjline %><br />
			</div>

			<%= main.isSelf? main.judgmentBarHTML(e): "" %>
		</div>
		<% }%>		


		<div><small>System message: <%= main.infomsg%> </small></div>

		<!-- #EndEditable -->	
	</div> <!-- Wrapping -->

<% } else { %>
<P>No suggestions for user <em><%= main.actorUserName %></em> has been generated yet. 
</p>
<% } %>
	


<h2><a name="tasks">(Re-)computing the suggestion list</a></h2>

<%    if (main.activeTask!=null) {
%> 
<p>
Presently, a task is running to generate the suggestion list: <%= main.activeTask%>. You may wait for a couple minutes, and then <a href="#refresh">refresh</a> this page to see if it has completed.</p>
<%      
      } else if (main.queuedTask!=null) {
%>
<p>
Presently, a task is queued to generate the suggestion list: <%= main.queuedTask%>. You may wait for a few minutes, and then reload this page to see if it has started and completed.</p>
 <%
       } else if (main.newTask!=null) {
%>
<p>
A new task has to generate the suggestion list has just been created and queued: <%= main.newTask%>. You may wait for a couple minutes, and then reload this page to see if it has started and completed.</p>
 <%
     }   else {
%>
Presently, no task has been scheduled to (re-)generate the suggestion list. You may want to request it now.
</p>
 <%      
      }
%>

<% if (main.newTask==null) { %>
<p>
Due to the computational costs, suggestion lists are created in an off-line mode. 
To create a new computational task to <%= (main.df==null) ? "create":"update" %>
the suggestion list for <em><%= main.actorUserName %></em>, click on the button below:
<form action="viewSuggestions.jsp">
<input type="hidden" name="<%=main.USER_NAME%>" value="<%=main.actorUserName%>">
<input type="hidden" name="<%=main.FORCE%>" value="true">
<input type="hidden" name="mode" value="<%=main.mode%>">
<input type="submit" value="Create task">
</form>
</p>

<hr>
<p>
<form action="viewSuggestions.jsp">
<input type="hidden" name="<%=main.USER_NAME%>" value="<%=main.actorUserName%>">
<input type="hidden" name="mode" value="<%=main.mode%>">
<a name="refresh"><input type="submit" value="Refresh view"></a>
</form>
</p>

<%  }  %>


<%  }  %>


<hr> <p><small>System message: <%= main.infomsg%> </small></p> <%  %>

<icd:RU/>

</body>

</html>
