$.validator.setDefaults({
	submitHandler: function() { alert("submitted!");        }
});

$().ready(function() {
	
	// validate signup form on keyup and submit
	$("#signupform").validate({
		rules: {
			user_name: {
				required: true,
				minlength: 2,
				nowhitespace: true
			},
			password: {
				required: true,
				minlength: 5
			},
			confirm_password: {
				required: true,
				minlength: 5,
				equalTo: "#password"
			},
			email: {
				required: true,
				email: true
			},
			
			confirm_email: {
				required: true,
				equalTo: "#email"
			}
		},

		messages: {
			username: {
				required: "Please enter a desired username.",
				minlength: "Your username must consist of at least 2 characters."
			},
			password: {
				required: "Please provide a desired password.",
				minlength: "Your desired password must be at least 5 characters long."
			},
			confirm_password: {
				required: "Please provide a desired password.",
				minlength: "Your desired password must be at least 5 characters long.",
				equalTo: "Please enter the same password as above."
			},
			email: {
				required: "Please enter a valid e-mail address."
			},			
			confirm_email: {
				required: "Please enter a valid e-mail address.",
				equalTo: "Please enter the same e-mail address as above."
			}
		}
	});
	
	
});
