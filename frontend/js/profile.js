const PROFILE_API_URL = 'http://127.0.0.1:8080/api';
const profileTokenKey = 'auctionSessionToken';
const profileMessage = document.querySelector('#profile-page-message');
const profileLoading = document.querySelector('#profile-loading');
const profileDisplay = document.querySelector('#profile-display');
const profileForm = document.querySelector('#profile-edit-form');
const profileEditButton = document.querySelector('#profile-edit-button');
const profileSaveButton = document.querySelector('#profile-save-button');
const profileNameInput = document.querySelector('#profile-edit-name');
const profileEmailInput = document.querySelector('#profile-edit-email');
const profilePasswordInput = document.querySelector('#profile-edit-password');
let profileRequestInProgress = false;
let currentProfile = null;

document.querySelector('#profile-edit-button').addEventListener('click', openProfileEditor);
document.querySelector('#profile-cancel-button').addEventListener('click', closeProfileEditor);
document.querySelector('#profile-edit-form').addEventListener('submit', saveProfile);
document.querySelector('#profile-logout-button').addEventListener('click', logoutProfilePage);
window.addEventListener('beforeunload', () => {
    document.body.classList.add('auth-pending');
});

async function profileRequest(path, options = {}) {
    const token = sessionStorage.getItem(profileTokenKey);
    const response = await fetch(`${PROFILE_API_URL}${path}`, {
        ...options,
        headers: {
            Accept: 'application/json',
            ...(options.headers || {}),
            ...(token ? { Authorization: `Bearer ${token}` } : {})
        }
    });
    let data;
    try {
        data = await response.json();
    } catch {
        throw new Error('The server returned an unreadable response.');
    }
    if (!response.ok) {
        const error = new Error(data && data.message ? data.message : 'The request could not be completed.');
        error.status = response.status;
        throw error;
    }
    return data;
}

function showProfileMessage(message, isError = false) {
    profileMessage.textContent = message;
    profileMessage.classList.toggle('error', isError);
    profileMessage.hidden = false;
}

function clearInvalidSession() {
    sessionStorage.removeItem(profileTokenKey);
    window.location.replace('login.html');
}

function displayProfile(profile) {
    currentProfile = profile;
    const initial = String(profile.name || 'U').trim().charAt(0).toUpperCase() || 'U';
    document.querySelector('#profile-header-avatar').textContent = initial;
    document.querySelector('#profile-page-avatar').textContent = initial;
    document.querySelector('#profile-header-name').textContent = profile.name;
    const role = String(profile.role || '').toUpperCase();
    document.querySelector('#profile-header-role').textContent = `Role: ${role}`;
    document.querySelector('#profile-page-name').textContent = profile.name;
    document.querySelector('#profile-page-email').textContent = profile.email;
    document.querySelector('#profile-page-role').textContent = role.charAt(0) + role.slice(1).toLowerCase();
    document.querySelector('#profile-page-id').textContent = String(profile.userId);
    profileNameInput.value = profile.name;
    profileEmailInput.value = profile.email;
    profilePasswordInput.value = '';
    profileLoading.hidden = true;
    profileDisplay.hidden = false;
}

async function loadProfilePage() {
    if (!sessionStorage.getItem(profileTokenKey)) {
        clearInvalidSession();
        return;
    }
    try {
        const data = await profileRequest('/profile');
        if (!data.profile || !data.profile.userId || !data.profile.role) {
            clearInvalidSession();
            return;
        }
        displayProfile(data.profile);
        document.body.classList.remove('auth-pending');
        if (new URLSearchParams(window.location.search).get('edit') === '1') {
            openProfileEditor();
        }
    } catch (error) {
        if (error.status === 401 || error.status === 404) {
            clearInvalidSession();
            return;
        }
        profileLoading.hidden = true;
        document.body.classList.remove('auth-pending');
        showProfileMessage(error.message || 'Your profile could not be loaded. Try again.', true);
    }
}

function openProfileEditor() {
    if (!currentProfile || profileRequestInProgress) return;
    profileMessage.hidden = true;
    profileDisplay.hidden = true;
    profileForm.hidden = false;
    profileNameInput.focus();
}

function closeProfileEditor() {
    if (currentProfile) displayProfile(currentProfile);
    profileForm.hidden = true;
    profileDisplay.hidden = false;
    profileMessage.hidden = true;
}

async function saveProfile(event) {
    event.preventDefault();
    if (profileRequestInProgress) return;
    const name = profileNameInput.value.trim();
    const email = profileEmailInput.value.trim();
    const password = profilePasswordInput.value;
    if (!name || !email) {
        showProfileMessage('Enter your name and a valid email address.', true);
        return;
    }
    profileRequestInProgress = true;
    profileSaveButton.disabled = true;
    profileSaveButton.textContent = 'Saving…';
    profileMessage.hidden = true;
    try {
        const data = await profileRequest('/profile', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, email, password })
        });
        if (!data.profile) throw new Error('The server did not return updated profile information.');
        displayProfile(data.profile);
        profileForm.hidden = true;
        profileDisplay.hidden = false;
        showProfileMessage('Profile updated successfully.');
        window.history.replaceState(null, '', 'profile.html');
    } catch (error) {
        if (error.status === 401) {
            clearInvalidSession();
        } else if (error.status === 409) {
            showProfileMessage('That email address is already used by another account.', true);
        } else if (error.status === 400) {
            showProfileMessage(error.message || 'Check your profile fields and try again.', true);
        } else {
            showProfileMessage(error.message || 'Your profile could not be updated. Try again.', true);
        }
    } finally {
        profileRequestInProgress = false;
        profileSaveButton.disabled = false;
        profileSaveButton.textContent = 'Save changes';
    }
}

async function logoutProfilePage() {
    const button = document.querySelector('#profile-logout-button');
    if (profileRequestInProgress) return;
    profileRequestInProgress = true;
    button.disabled = true;
    button.textContent = 'Logging out…';
    try {
        await profileRequest('/logout', { method: 'POST' });
        sessionStorage.removeItem(profileTokenKey);
        window.location.replace('login.html');
    } catch (error) {
        if (error.status === 401) {
            clearInvalidSession();
        } else {
            showProfileMessage(error.message || 'Logout could not be completed. Try again.', true);
        }
    } finally {
        profileRequestInProgress = false;
        button.disabled = false;
        button.textContent = 'Log out';
    }
}

loadProfilePage();
