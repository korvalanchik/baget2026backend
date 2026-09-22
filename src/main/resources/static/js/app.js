function checkAuthentication() {
    const token = localStorage.getItem('jwtToken');

    if (!token) {
    alert('You are not authenticated. Redirecting to login page.');
    window.location.href = '/auth/login'; // Redirect to login page if not authenticated
    } else {
    // Optionally, you can validate the token with the server here
        fetch('/auth/validate', {
                                    method: 'POST',
                                    headers: {
                                        'Content-Type': 'application/json',
                                        'Authorization': 'Bearer ' + token
                                    }
                                }
        )
        .then(response => {
                if (!response.ok) {
                    throw new Error('Token validation failed');
                }
                return response.json();
            }
        )
        .then(data => {
                console.log('Token is valid');
            }
        )
        .catch(error => {
                console.error('Error:', error);
                alert('Your session has expired. Please log in again.');
                localStorage.removeItem('jwtToken');
                window.location.href = '/auth/login'; // Redirect to login page if token is invalid
            }
        );
    }
}

// Call checkAuthentication when the page loads
document.addEventListener('DOMContentLoaded', checkAuthentication);
