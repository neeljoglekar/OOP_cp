const API_BASE_URL = 'http://127.0.0.1:8080/api';

const apiStatus = document.querySelector('#api-status');
const auctionList = document.querySelector('#auction-list');
const auctionCount = document.querySelector('#auction-count');
const pageMessage = document.querySelector('#page-message');
const detailsSection = document.querySelector('#auction-details');
const detailsContent = document.querySelector('#details-content');
const bidForm = document.querySelector('#bid-form');
const bidAccessMessage = document.querySelector('#bid-access-message');
const bidAccount = document.querySelector('#bid-account');
const bidFields = bidForm.querySelector('.form-fields');
const bidAmountInput = document.querySelector('#bid-amount');
const placeBidButton = document.querySelector('#place-bid-button');
const bidMessage = document.querySelector('#bid-message');
const loginForm = document.querySelector('#login-form');
const loginEmailInput = document.querySelector('#login-email');
const loginPasswordInput = document.querySelector('#login-password');
const loginButton = document.querySelector('#login-button');
const loginMessage = document.querySelector('#login-message');
const authOptions = document.querySelector('#auth-options');
const registrationForm = document.querySelector('#registration-form');
const registrationUserId = document.querySelector('#registration-user-id');
const registrationName = document.querySelector('#registration-name');
const registrationEmail = document.querySelector('#registration-email');
const registrationPassword = document.querySelector('#registration-password');
const registrationRole = document.querySelector('#registration-role');
const registerButton = document.querySelector('#register-button');
const registrationMessage = document.querySelector('#registration-message');
const loggedInPanel = document.querySelector('#logged-in-panel');
const loggedInName = document.querySelector('#logged-in-name');
const loggedInRole = document.querySelector('#logged-in-role');
const loggedInId = document.querySelector('#logged-in-id');
const logoutButton = document.querySelector('#logout-button');
const profilePanel = document.querySelector('#profile-panel');
const profileMessage = document.querySelector('#profile-message');
const profileForm = document.querySelector('#profile-form');
const profileEditName = document.querySelector('#profile-edit-name');
const profileEditEmail = document.querySelector('#profile-edit-email');
const profileEditPassword = document.querySelector('#profile-edit-password');
const profileEditMessage = document.querySelector('#profile-edit-message');
const viewProfileButton = document.querySelector('#view-profile-button');
const editProfileButton = document.querySelector('#edit-profile-button');
const saveProfileButton = document.querySelector('#save-profile-button');
const sellerDashboard = document.querySelector('#seller-dashboard');
const showCreateAuctionButton = document.querySelector('#show-create-auction-button');
const createAuctionForm = document.querySelector('#create-auction-form');
const createAuctionIdInput = document.querySelector('#create-auction-id');
const createProductIdInput = document.querySelector('#create-product-id');
const createProductNameInput = document.querySelector('#create-product-name');
const createProductDescriptionInput = document.querySelector('#create-product-description');
const createProductConditionInput = document.querySelector('#create-product-condition');
const createAuctionEndTimeInput = document.querySelector('#create-auction-end-time');
const createAuctionIncrementInput = document.querySelector('#create-auction-increment');
const createAuctionButton = document.querySelector('#create-auction-submit');
const cancelCreateAuctionButton = document.querySelector('#cancel-create-auction-button');
const sellerAuctionMessage = document.querySelector('#seller-auction-message');
const liveUpdateStatus = document.querySelector('#live-update-status');
const lastUpdated = document.querySelector('#last-updated');

const AUCTION_POLL_INTERVAL_MS = 1000;
let selectedAuctionId = null;
let bidRequestInProgress = false;
let auctionPollingTimer = null;
let auctionListPollingTimer = null;
let auctionListLoadPromise = null;
let pollingAuctionId = null;
let pollRequestInProgress = false;
let selectedEndTime = null;
let currentUser = null;
let authenticationRequestInProgress = false;
let profileRequestInProgress = false;
let authMode = 'login';
let profileLoaded = false;
let profileEditorOpen = false;
let auctionCreationInProgress = false;
let createAuctionFormOpen = false;

document.querySelector('#close-details').addEventListener('click', () => {
    if (bidRequestInProgress) {
        showBidMessage('Please wait for the current bid request to finish.', true);
        return;
    }
    detailsSection.hidden = true;
    selectedAuctionId = null;
    selectedEndTime = null;
    stopAuctionPolling();
});

bidForm.addEventListener('submit', submitBid);
loginForm.addEventListener('submit', submitLogin);
registrationForm.addEventListener('submit', submitRegistration);
logoutButton.addEventListener('click', submitLogout);
document.querySelector('#show-register-button').addEventListener('click', () => setAuthMode('register'));
document.querySelector('#show-login-button').addEventListener('click', () => setAuthMode('login'));
viewProfileButton.addEventListener('click', loadProfile);
editProfileButton.addEventListener('click', openProfileEditor);
profileForm.addEventListener('submit', submitProfileUpdate);
showCreateAuctionButton.addEventListener('click', () => {
    createAuctionFormOpen = !createAuctionFormOpen;
    sellerAuctionMessage.hidden = true;
    updateAuthenticationUI();
});
createAuctionForm.addEventListener('submit', submitAuctionCreation);
cancelCreateAuctionButton.addEventListener('click', () => {
    createAuctionFormOpen = false;
    createAuctionForm.reset();
    sellerAuctionMessage.hidden = true;
    updateAuthenticationUI();
});
document.querySelector('#cancel-profile-button').addEventListener('click', () => {
    profileForm.hidden = true;
    profileEditMessage.hidden = true;
});
window.addEventListener('beforeunload', () => {
    stopAuctionPolling();
    stopAuctionListPolling();
});

function setApiStatus(connected) {
    apiStatus.classList.toggle('connected', connected);
    apiStatus.classList.toggle('disconnected', !connected);
    apiStatus.lastChild.textContent = connected ? ' API Status: Connected' : ' API Status: Disconnected';
}

function showMessage(message, type = '') {
    pageMessage.textContent = message;
    pageMessage.className = `message ${type}`.trim();
    pageMessage.hidden = false;
}

function hideMessage() {
    pageMessage.hidden = true;
    pageMessage.textContent = '';
}

async function fetchJson(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: {
            Accept: 'application/json',
            ...(options.headers || {})
        }
    });
    let data;
    try {
        data = await response.json();
    } catch {
        const error = new Error('The auction server returned an unreadable response.');
        error.status = response.status;
        error.responseMessage = '';
        throw error;
    }
    if (!response.ok) {
        const error = new Error('The auction server could not complete the request.');
        error.status = response.status;
        error.responseMessage = data && typeof data.message === 'string' ? data.message : '';
        throw error;
    }
    return data;
}

function getSessionToken() {
    return sessionStorage.getItem('auctionSessionToken');
}

async function fetchAuthenticatedJson(url, options = {}) {
    const token = getSessionToken();
    const headers = { ...(options.headers || {}) };
    if (token) headers.Authorization = `Bearer ${token}`;
    try {
        return await fetchJson(url, { ...options, headers });
    } catch (error) {
        if (error && error.status === 401) {
            clearBrowserSession();
            showLoginMessage('Your login session has ended. Please log in again.');
        }
        throw error;
    }
}

function clearBrowserSession() {
    sessionStorage.removeItem('auctionSessionToken');
    currentUser = null;
    profileLoaded = false;
    profileEditorOpen = false;
    bidAmountInput.value = '';
    clearBidMessage();
    updateAuthenticationUI();
}

function friendlyApiError(error, action) {
    if (!error || typeof error.status !== 'number') {
        return 'Unable to reach the auction server. Please make sure it is running and try again.';
    }
    if (error.message === 'The auction server returned an unreadable response.') {
        return 'The auction server returned an unreadable response. Please try again later.';
    }

    const serverMessage = (error.responseMessage || '').toLowerCase();
    if (action === 'login') {
        if (error.status === 400) return 'Enter a valid email and password.';
        if (error.status === 401) return 'The email or password was incorrect.';
        if (error.status >= 500) return 'Login is temporarily unavailable. Please try again later.';
        return 'Unable to log in. Please try again.';
    }
    if (action === 'logout') {
        return 'Logout could not be completed. Please try again.';
    }
    if (action === 'register') {
        if (error.status === 400) {
            return 'Check the registration fields. Public accounts can use the Buyer or Seller role.';
        }
        if (error.status === 409) return 'That user ID or email is already registered.';
        if (error.status >= 500) return 'Registration is temporarily unavailable. Please try again later.';
        return 'Registration could not be completed. Please check the details and try again.';
    }
    if (action === 'profile' || action === 'profile-update') {
        if (error.status === 400) return 'Check that the name and email fields are valid.';
        if (error.status === 401) return 'Please log in again to access your profile.';
        if (error.status === 403) return 'You are not allowed to access this profile.';
        if (error.status === 404) return 'The profile could not be found. Please log in again.';
        if (error.status === 409) return 'That email address is already used by another account.';
        if (error.status >= 500) return 'The profile service is temporarily unavailable.';
        return 'The profile request could not be completed. Please try again.';
    }
    if (action === 'auction-create') {
        if (error.status === 400) {
            return error.responseMessage || 'Check that all auction details are valid and the end time is in the future.';
        }
        if (error.status === 401) return 'Log in with a seller account to create an auction.';
        if (error.status === 403) return 'Only a seller can create an auction.';
        if (error.status === 409) return 'That auction ID is already in use.';
        if (error.status === 500) return 'The auction could not be created because of a server problem.';
        return 'The auction could not be created. Check the details and try again.';
    }
    if (error.status === 400) {
        if (serverMessage.includes('minimum next bid')) {
            return 'Your bid is below the required minimum increment for this auction.';
        }
        if (serverMessage.includes('higher than the current highest bid')) {
            return 'Your bid must be higher than the current highest bid.';
        }
        if (serverMessage.includes('buyerid') || serverMessage.includes('request body')
                || serverMessage.includes('json')) {
            return 'The bid request is not in a valid format. Check the buyer ID and bid amount.';
        }
        return 'The bid amount or bidder information is invalid. Check your entries and try again.';
    }
    if (error.status === 403) {
        return 'This request is not allowed. Only a buyer can place a bid.';
    }
    if (error.status === 404) {
        if (serverMessage.includes('user')) return 'That buyer ID was not found.';
        if (serverMessage.includes('auction') || action === 'details') {
            return 'The selected auction could not be found.';
        }
        return 'The auction or buyer could not be found.';
    }
    if (error.status === 409) {
        return 'This auction is closed or has expired and can no longer accept bids.';
    }
    if (error.status >= 500) {
        return 'The auction server could not complete the request. Please try again later.';
    }
    return 'The auction server could not complete the request. Please try again.';
}

function isLoggedInBuyer() {
    return Boolean(currentUser && String(currentUser.role).toUpperCase() === 'BUYER');
}

function isLoggedInSeller() {
    return Boolean(currentUser && String(currentUser.role).toUpperCase() === 'SELLER');
}

function updateAuthenticationUI() {
    const loggedIn = Boolean(currentUser);
    authOptions.hidden = loggedIn;
    loginForm.hidden = loggedIn || authMode !== 'login';
    registrationForm.hidden = loggedIn || authMode !== 'register';
    loggedInPanel.hidden = !loggedIn;
    profilePanel.hidden = !loggedIn || !profileLoaded;
    profileForm.hidden = !loggedIn || !profileEditorOpen;
    sellerDashboard.hidden = !isLoggedInSeller();
    createAuctionForm.hidden = !isLoggedInSeller() || !createAuctionFormOpen;
    showCreateAuctionButton.disabled = auctionCreationInProgress;
    createAuctionButton.disabled = auctionCreationInProgress;
    if (loggedIn) {
        loggedInName.textContent = displayValue(currentUser.name);
        loggedInRole.textContent = `Role: ${String(currentUser.role).toUpperCase()}`;
        loggedInId.textContent = `User ID: ${currentUser.userId}`;
    } else {
        loggedInName.textContent = '';
        loggedInRole.textContent = '';
        loggedInId.textContent = '';
        profileLoaded = false;
        profileEditorOpen = false;
        profileMessage.hidden = true;
        profileEditMessage.hidden = true;
        profileForm.reset();
        createAuctionFormOpen = false;
        createAuctionForm.reset();
        sellerAuctionMessage.hidden = true;
    }

    const canBid = isLoggedInBuyer();
    bidFields.hidden = !canBid;
    if (!loggedIn) {
        bidAccessMessage.textContent = 'Please log in as a buyer to place a bid.';
    } else if (!canBid) {
        bidAccessMessage.textContent = 'Only logged-in buyers can place bids.';
    } else {
        bidAccessMessage.textContent = 'Your logged-in Buyer account will be used for this bid.';
    }
    bidAccount.textContent = canBid
        ? `Bidding as ${currentUser.name} · ID ${currentUser.userId}` : '';
    placeBidButton.disabled = !canBid || bidRequestInProgress;
}

async function submitAuctionCreation(event) {
    event.preventDefault();
    if (auctionCreationInProgress) return;
    if (!isLoggedInSeller()) {
        showAccountMessage(sellerAuctionMessage,
            'Log in with a seller account to create an auction.', true);
        return;
    }

    const auctionId = createAuctionIdInput.value.trim();
    const productId = createProductIdInput.value.trim();
    const productName = createProductNameInput.value.trim();
    const description = createProductDescriptionInput.value.trim();
    const condition = createProductConditionInput.value.trim();
    const endTime = createAuctionEndTimeInput.value;
    const incrementText = createAuctionIncrementInput.value.trim();
    if (!auctionId || !productId || !productName || !description || !condition
            || !endTime || !incrementText) {
        showAccountMessage(sellerAuctionMessage, 'Complete every auction and product field.', true);
        return;
    }
    const endDate = new Date(endTime);
    if (Number.isNaN(endDate.getTime()) || endDate.getTime() <= Date.now()) {
        showAccountMessage(sellerAuctionMessage, 'Choose an auction end time in the future.', true);
        return;
    }
    const minimumBidIncrement = Number(incrementText);
    if (!Number.isFinite(minimumBidIncrement) || minimumBidIncrement <= 0) {
        showAccountMessage(sellerAuctionMessage,
            'Minimum bid increment must be a positive finite number.', true);
        return;
    }

    auctionCreationInProgress = true;
    createAuctionButton.textContent = 'Creating…';
    sellerAuctionMessage.hidden = true;
    updateAuthenticationUI();
    try {
        const data = await fetchAuthenticatedJson(`${API_BASE_URL}/auctions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                auctionId,
                productId,
                productName,
                description,
                condition,
                endTime,
                minimumBidIncrement
            })
        });
        createAuctionForm.reset();
        showAccountMessage(sellerAuctionMessage,
            data.message
                ? `${data.message} ${productName} (${auctionId}) ends ${formatDate(endTime)}; minimum increment ${formatAmount(minimumBidIncrement)}.`
                : `Auction ${auctionId} created for ${productName}. Ends ${formatDate(endTime)}; minimum increment ${formatAmount(minimumBidIncrement)}.`,
            false);
        const refreshed = await loadAuctions();
        if (!refreshed) {
            showAccountMessage(sellerAuctionMessage,
                'Auction created, but the auction list could not be refreshed.', false);
        }
    } catch (error) {
        showAccountMessage(sellerAuctionMessage, friendlyApiError(error, 'auction-create'), true);
        if (error && typeof error.status !== 'number') setApiStatus(false);
    } finally {
        auctionCreationInProgress = false;
        createAuctionButton.textContent = 'Create auction';
        updateAuthenticationUI();
    }
}

function setAuthMode(mode) {
    authMode = mode;
    loginMessage.hidden = true;
    registrationMessage.hidden = true;
    updateAuthenticationUI();
}

function showLoginMessage(message, isError = true) {
    loginMessage.textContent = message;
    loginMessage.classList.toggle('error', isError);
    loginMessage.hidden = false;
}

async function submitLogin(event) {
    event.preventDefault();
    if (authenticationRequestInProgress) return;
    const email = loginEmailInput.value.trim();
    const password = loginPasswordInput.value;
    if (!email || !password) {
        showLoginMessage('Enter both your email and password.');
        return;
    }

    authenticationRequestInProgress = true;
    loginButton.disabled = true;
    loginButton.textContent = 'Logging in…';
    loginMessage.hidden = true;
    try {
        const data = await fetchJson(`${API_BASE_URL}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });
        if (typeof data.sessionToken !== 'string' || !data.sessionToken
                || !data.user || !data.user.userId || !data.user.name
                || !data.user.email || !data.user.role) {
            throw new Error('The login response did not contain account/session information.');
        }
        sessionStorage.setItem('auctionSessionToken', data.sessionToken);
        currentUser = data.user;
        profileLoaded = false;
        profileEditorOpen = false;
        updateAuthenticationUI();
        loginPasswordInput.value = '';
        showLoginMessage('Login successful.', false);
    } catch (error) {
        loginPasswordInput.value = '';
        showLoginMessage(friendlyApiError(error, 'login'));
        if (error && typeof error.status !== 'number') setApiStatus(false);
    } finally {
        authenticationRequestInProgress = false;
        loginButton.disabled = false;
        loginButton.textContent = 'Log in';
    }
}

async function submitRegistration(event) {
    event.preventDefault();
    if (authenticationRequestInProgress) return;
    const userIdText = registrationUserId.value.trim();
    const userId = Number(userIdText);
    const name = registrationName.value.trim();
    const email = registrationEmail.value.trim();
    const password = registrationPassword.value;
    const role = registrationRole.value;
    if (!userIdText || !Number.isSafeInteger(userId) || userId <= 0
            || !name || !email || !password || !role) {
        showAccountMessage(registrationMessage,
            'Enter a positive whole-number user ID, name, email, password, and role.', true);
        return;
    }
    if (role !== 'BUYER' && role !== 'SELLER') {
        showAccountMessage(registrationMessage, 'Choose Buyer or Seller for public registration.', true);
        return;
    }

    authenticationRequestInProgress = true;
    registerButton.disabled = true;
    registerButton.textContent = 'Registering…';
    registrationMessage.hidden = true;
    try {
        const data = await fetchJson(`${API_BASE_URL}/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ userId, name, email, password, role })
        });
        registrationPassword.value = '';
        registrationForm.reset();
        setAuthMode('login');
        loginEmailInput.value = email;
        showAccountMessage(loginMessage,
            data.message || 'Registration successful. You can now log in.', false);
    } catch (error) {
        registrationPassword.value = '';
        showAccountMessage(registrationMessage, friendlyApiError(error, 'register'), true);
        if (error && typeof error.status !== 'number') setApiStatus(false);
    } finally {
        authenticationRequestInProgress = false;
        registerButton.disabled = false;
        registerButton.textContent = 'Register';
    }
}

async function submitLogout() {
    if (authenticationRequestInProgress) return;
    if (bidRequestInProgress) {
        showBidMessage('Please wait for the current bid request to finish.', true);
        return;
    }
    authenticationRequestInProgress = true;
    logoutButton.disabled = true;
    logoutButton.textContent = 'Logging out…';
    try {
        await fetchAuthenticatedJson(`${API_BASE_URL}/logout`, { method: 'POST' });
        clearBrowserSession();
        showLoginMessage('You have logged out.', false);
    } catch (error) {
        showLoginMessage(friendlyApiError(error, 'logout'));
        if (error && typeof error.status !== 'number') setApiStatus(false);
    } finally {
        authenticationRequestInProgress = false;
        logoutButton.disabled = false;
        logoutButton.textContent = 'Log out';
    }
}

function showAccountMessage(element, message, isError) {
    element.textContent = message;
    element.classList.toggle('error', isError);
    element.hidden = false;
}

function renderProfile(profile) {
    document.querySelector('#profile-user-id').textContent = displayValue(profile.userId);
    document.querySelector('#profile-name').textContent = displayValue(profile.name);
    document.querySelector('#profile-email').textContent = displayValue(profile.email);
    document.querySelector('#profile-role').textContent = displayValue(profile.role).toUpperCase();
    currentUser = { ...currentUser, ...profile };
    profileLoaded = true;
    updateAuthenticationUI();
}

async function loadProfile() {
    if (!currentUser || profileRequestInProgress) return;
    profileRequestInProgress = true;
    viewProfileButton.disabled = true;
    profileMessage.hidden = true;
    try {
        const data = await fetchAuthenticatedJson(`${API_BASE_URL}/profile`);
        if (!data.profile || !data.profile.userId || !data.profile.role) {
            throw new Error('The profile response did not contain account information.');
        }
        renderProfile(data.profile);
        profileEditorOpen = false;
        updateAuthenticationUI();
    } catch (error) {
        showAccountMessage(profileMessage, friendlyApiError(error, 'profile'), true);
        if (error && typeof error.status !== 'number') setApiStatus(false);
    } finally {
        profileRequestInProgress = false;
        viewProfileButton.disabled = false;
    }
}

async function openProfileEditor() {
    if (!currentUser || profileRequestInProgress) return;
    profileRequestInProgress = true;
    editProfileButton.disabled = true;
    profileEditMessage.hidden = true;
    try {
        const data = await fetchAuthenticatedJson(`${API_BASE_URL}/profile`);
        if (!data.profile || !data.profile.userId || !data.profile.role) {
            throw new Error('The profile response did not contain account information.');
        }
        renderProfile(data.profile);
        profileEditName.value = data.profile.name;
        profileEditEmail.value = data.profile.email;
        profileEditPassword.value = '';
        profileEditorOpen = true;
        updateAuthenticationUI();
    } catch (error) {
        showAccountMessage(profileEditMessage, friendlyApiError(error, 'profile'), true);
        if (error && typeof error.status !== 'number') setApiStatus(false);
    } finally {
        profileRequestInProgress = false;
        editProfileButton.disabled = false;
    }
}

async function submitProfileUpdate(event) {
    event.preventDefault();
    if (!currentUser || profileRequestInProgress) return;
    const name = profileEditName.value.trim();
    const email = profileEditEmail.value.trim();
    const password = profileEditPassword.value;
    if (!name || !email) {
        showAccountMessage(profileEditMessage, 'Name and email are required.', true);
        return;
    }
    profileRequestInProgress = true;
    saveProfileButton.disabled = true;
    saveProfileButton.textContent = 'Saving…';
    profileEditMessage.hidden = true;
    try {
        const data = await fetchAuthenticatedJson(`${API_BASE_URL}/profile`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, email, password })
        });
        profileEditPassword.value = '';
        renderProfile(data.profile);
        profileEditorOpen = false;
        updateAuthenticationUI();
        showAccountMessage(profileMessage, data.message || 'Profile updated successfully.', false);
    } catch (error) {
        profileEditPassword.value = '';
        showAccountMessage(profileEditMessage, friendlyApiError(error, 'profile-update'), true);
        if (error && typeof error.status !== 'number') setApiStatus(false);
    } finally {
        profileRequestInProgress = false;
        saveProfileButton.disabled = false;
        saveProfileButton.textContent = 'Save changes';
    }
}

function displayValue(value, fallback = 'Not available') {
    return value === null || value === undefined || value === '' ? fallback : String(value);
}

function formatAmount(amount) {
    if (typeof amount !== 'number' || !Number.isFinite(amount)) {
        return 'No bids yet';
    }
    return new Intl.NumberFormat(undefined, {
        useGrouping: true,
        maximumFractionDigits: 2
    }).format(amount);
}

function formatDate(value) {
    if (!value) return 'Not set';
    const date = parseApiDateTime(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short'
    }).format(date);
}

function parseApiDateTime(value) {
    if (typeof value !== 'string') return new Date(Number.NaN);
    // Java LocalDateTime may include nanoseconds; JavaScript Date has millisecond precision.
    const millisecondValue = value.replace(/(\.\d{3})\d+$/, '$1');
    return new Date(millisecondValue);
}

function formatCountdown(value) {
    const endDate = parseApiDateTime(value);
    if (Number.isNaN(endDate.getTime())) return 'End time unavailable';

    const remainingSeconds = Math.ceil((endDate.getTime() - Date.now()) / 1000);
    if (remainingSeconds <= 0) return 'Auction ended';

    const days = Math.floor(remainingSeconds / 86400);
    const hours = Math.floor((remainingSeconds % 86400) / 3600);
    const minutes = Math.floor((remainingSeconds % 3600) / 60);
    const seconds = remainingSeconds % 60;
    if (days > 0) return `${days}d ${hours}h ${minutes}m ${seconds}s`;
    if (hours > 0) return `${hours}h ${minutes}m ${seconds}s`;
    if (minutes > 0) return `${minutes}m ${seconds}s`;
    return `${seconds}s`;
}

function createElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined) element.textContent = text;
    return element;
}

function addFact(container, label, value) {
    const fact = createElement('div');
    fact.append(createElement('span', 'fact-label', label));
    fact.append(createElement('span', 'fact-value', value));
    container.append(fact);
}

function getHighestBidAmount(auction) {
    const highest = auction.currentHighestBid;
    return highest && typeof highest.amount === 'number' ? highest.amount : null;
}

function renderAuction(auction) {
    const product = auction.product || {};
    const highestBid = auction.currentHighestBid;
    const status = displayValue(auction.status, 'Unknown').toLowerCase();
    const card = createElement('article', 'auction-card');
    const topLine = createElement('div', 'card-topline');
    topLine.append(createElement('span', 'product-condition', displayValue(product.condition, 'Condition TBD')));
    topLine.append(createElement('span', `auction-status ${status}`, displayValue(auction.status, 'Unknown')));

    const title = createElement('h3', '', displayValue(product.name, 'Untitled product'));
    const description = createElement('p', 'product-description', displayValue(product.description, 'No description provided.'));
    const facts = createElement('div', 'auction-facts');
    addFact(facts, 'Current highest bid', formatAmount(getHighestBidAmount(auction)));
    addFact(facts, 'Highest bidder', displayValue(
        highestBid && highestBid.buyerName ? highestBid.buyerName : null, 'No bids yet'));
    addFact(facts, 'Winner', displayValue(
        auction.winner && auction.winner.name ? auction.winner.name : null, 'Not determined'));
    addFact(facts, 'Minimum increment', formatAmount(auction.minimumBidIncrement));
    addFact(facts, 'Auction ends', formatDate(auction.endTime));

    const actions = createElement('div', 'card-actions');
    actions.append(createElement('span', 'auction-id', `Auction ${displayValue(auction.auctionId, '—')}`));
    const button = createElement('button', 'button', 'View auction');
    button.type = 'button';
    button.addEventListener('click', () => loadAuctionDetails(auction.auctionId));
    actions.append(button);

    card.append(topLine, title, description, facts, actions);
    return card;
}

function renderAuctions(data) {
    if (!data || !Array.isArray(data.auctions)) {
        throw new Error('The auction server returned auction data in an unexpected format.');
    }

    auctionList.replaceChildren();
    auctionCount.textContent = `${data.auctions.length} ${data.auctions.length === 1 ? 'auction' : 'auctions'}`;
    if (data.auctions.length === 0) {
        showMessage('There are no auctions available right now.', 'empty');
        return;
    }

    hideMessage();
    data.auctions.forEach((auction) => auctionList.append(renderAuction(auction)));
}

function appendDetail(container, label, value) {
    const item = createElement('div');
    item.append(createElement('dt', '', label));
    const detailValue = createElement('dd', '', value);
    item.append(detailValue);
    container.append(item);
    return detailValue;
}

function renderDetails(auction, shouldScroll = false) {
    const product = auction.product || {};
    const highestBid = auction.currentHighestBid;
    selectedEndTime = auction.endTime || null;
    detailsContent.replaceChildren();
    detailsContent.append(createElement('h3', '', displayValue(product.name, 'Untitled product')));
    detailsContent.append(createElement('p', '', displayValue(product.description, 'No description provided.')));

    const facts = createElement('dl', 'details-list');
    appendDetail(facts, 'Auction ID', displayValue(auction.auctionId));
    appendDetail(facts, 'Product ID', displayValue(product.productId));
    appendDetail(facts, 'Condition', displayValue(product.condition));
    appendDetail(facts, 'Current highest bid', formatAmount(getHighestBidAmount(auction)));
    appendDetail(facts, 'Current high bidder', displayValue(
        highestBid && highestBid.buyerName ? highestBid.buyerName : null, 'No bids yet'));
    appendDetail(facts, 'Minimum bid increment', formatAmount(auction.minimumBidIncrement));
    appendDetail(facts, 'Ends', formatDate(auction.endTime));
    const countdown = appendDetail(facts, 'Time remaining', formatCountdown(selectedEndTime));
    countdown.id = 'auction-countdown';
    countdown.className = 'countdown-value';
    appendDetail(facts, 'Status', displayValue(auction.status));
    appendDetail(facts, 'Winner', auction.winner && auction.winner.name
        ? auction.winner.name : 'Not determined');
    detailsContent.append(facts);
    detailsSection.hidden = false;
    if (shouldScroll) detailsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    updateCountdown();
}

async function loadAuctionDetails(auctionId) {
    if (bidRequestInProgress) {
        showBidMessage('Please wait for the current bid request to finish.', true);
        return;
    }
    if (!auctionId) {
        showMessage('This auction does not have a valid ID.', 'error');
        return;
    }
    stopAuctionPolling();
    selectedAuctionId = String(auctionId);
    clearBidMessage();
    bidForm.hidden = true;
    detailsContent.replaceChildren();
    selectedEndTime = null;
    lastUpdated.textContent = 'Last updated: —';
    setLiveUpdateStatus('Loading auction…', 'updating');
    try {
        const data = await fetchJson(`${API_BASE_URL}/auctions/${encodeURIComponent(selectedAuctionId)}`);
        if (!data.auction || typeof data.auction !== 'object') {
            throw new Error('The auction server returned an unreadable response.');
        }
        renderDetails(data.auction, true);
        bidForm.hidden = false;
        noteAuctionUpdated();
        setApiStatus(true);
        startAuctionPolling(selectedAuctionId);
    } catch (error) {
        showBidMessage(friendlyApiError(error, 'details'), true);
        if (error && typeof error.status !== 'number') setApiStatus(false);
        detailsSection.hidden = false;
        setLiveUpdateStatus('Live updates: OFF');
    }
}

function setLiveUpdateStatus(message, state = '') {
    liveUpdateStatus.textContent = message;
    liveUpdateStatus.className = `live-update-status ${state}`.trim();
}

function noteAuctionUpdated() {
    lastUpdated.textContent = `Last updated: ${new Intl.DateTimeFormat(undefined, {
        hour: '2-digit', minute: '2-digit', second: '2-digit'
    }).format(new Date())}`;
}

function updateCountdown() {
    const countdown = detailsContent.querySelector('#auction-countdown');
    if (countdown && selectedEndTime) countdown.textContent = formatCountdown(selectedEndTime);
}

function stopAuctionPolling() {
    if (auctionPollingTimer !== null) {
        window.clearInterval(auctionPollingTimer);
        auctionPollingTimer = null;
    }
    pollingAuctionId = null;
    setLiveUpdateStatus('Live updates: OFF');
}

function startAuctionPolling(auctionId) {
    stopAuctionPolling();
    const currentAuctionId = String(auctionId);
    pollingAuctionId = currentAuctionId;
    setLiveUpdateStatus('Live updates: ON', 'live');
    auctionPollingTimer = window.setInterval(() => {
        updateCountdown();
        pollSelectedAuction(currentAuctionId);
    }, AUCTION_POLL_INTERVAL_MS);
}

async function pollSelectedAuction(auctionId) {
    if (!auctionId || auctionId !== selectedAuctionId || auctionId !== pollingAuctionId
            || pollRequestInProgress || bidRequestInProgress) {
        return;
    }

    pollRequestInProgress = true;
    setLiveUpdateStatus('Updating…', 'updating');
    try {
        const data = await fetchJson(`${API_BASE_URL}/auctions/${encodeURIComponent(auctionId)}`);
        if (auctionId !== selectedAuctionId || auctionId !== pollingAuctionId
                || bidRequestInProgress) {
            return;
        }
        if (!data.auction || typeof data.auction !== 'object') {
            throw new Error('The auction server returned an unreadable response.');
        }
        renderDetails(data.auction);
        noteAuctionUpdated();
        setLiveUpdateStatus('Live updates: ON', 'live');
        setApiStatus(true);
    } catch (error) {
        if (auctionId === selectedAuctionId && auctionId === pollingAuctionId) {
            setLiveUpdateStatus('Connection problem — retrying', 'problem');
            if (error && typeof error.status !== 'number') setApiStatus(false);
        }
    } finally {
        pollRequestInProgress = false;
    }
}

function showBidMessage(message, isError = false) {
    bidMessage.textContent = message;
    bidMessage.classList.toggle('error', isError);
    bidMessage.hidden = false;
}

function clearBidMessage() {
    bidMessage.textContent = '';
    bidMessage.classList.remove('error');
    bidMessage.hidden = true;
}

async function submitBid(event) {
    event.preventDefault();
    if (bidRequestInProgress) return;

    if (!selectedAuctionId) {
        showBidMessage('Select an auction before placing a bid.', true);
        return;
    }
    const auctionId = selectedAuctionId;

    const amountText = bidAmountInput.value.trim();
    if (!isLoggedInBuyer()) {
        showBidMessage(currentUser
            ? 'Only logged-in buyers can place bids.'
            : 'Please log in as a buyer to place a bid.', true);
        return;
    }
    if (!amountText) {
        showBidMessage('Enter a bid amount.', true);
        return;
    }

    bidRequestInProgress = true;
    placeBidButton.disabled = true;
    placeBidButton.textContent = 'Submitting…';
    setLiveUpdateStatus('Bid in progress', 'updating');
    clearBidMessage();

    try {
        const requestBody = { amount: Number(amountText) };
        await fetchAuthenticatedJson(`${API_BASE_URL}/auctions/${encodeURIComponent(auctionId)}/bids`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestBody)
        });

        bidAmountInput.value = '';
        showBidMessage('Bid accepted. Refreshing auction details.');
        try {
            const refreshedData = await fetchJson(
                `${API_BASE_URL}/auctions/${encodeURIComponent(auctionId)}`);
            if (!refreshedData.auction || typeof refreshedData.auction !== 'object') {
                throw new Error('The auction server returned an unreadable response.');
            }
            renderDetails(refreshedData.auction);
            noteAuctionUpdated();
            showBidMessage('Bid placed successfully. Auction details have been updated.');
            setLiveUpdateStatus('Live updates: ON', 'live');
        } catch {
            showBidMessage('Bid placed successfully, but the latest auction details could not be loaded. Please select the auction again.');
            setLiveUpdateStatus('Connection problem — retrying', 'problem');
        }
    } catch (error) {
        showBidMessage(friendlyApiError(error, 'bid'), true);
        if (error && typeof error.status !== 'number') setApiStatus(false);
        setLiveUpdateStatus('Live updates: ON', 'live');
    } finally {
        bidRequestInProgress = false;
        placeBidButton.disabled = false;
        placeBidButton.textContent = 'Place bid';
        updateAuthenticationUI();
    }
}

async function checkApiHealth() {
    try {
        const data = await fetchJson(`${API_BASE_URL}/health`);
        if (data.success !== true) throw new Error('Health check was not successful.');
        setApiStatus(true);
        return true;
    } catch {
        setApiStatus(false);
        return false;
    }
}

async function loadAuctions() {
    if (auctionListLoadPromise) return auctionListLoadPromise;

    auctionListLoadPromise = (async () => {
        try {
            const data = await fetchJson(`${API_BASE_URL}/auctions`);
            renderAuctions(data);
            setApiStatus(true);
            return true;
        } catch (error) {
            auctionList.replaceChildren();
            auctionCount.textContent = '';
            showMessage(error.message === 'The auction server returned an unreadable response.'
                ? 'The auction server returned an unreadable response.'
                : 'Unable to load auctions. Please check that the auction server is running.', 'error');
            setApiStatus(false);
            return false;
        } finally {
            auctionListLoadPromise = null;
        }
    })();
    return auctionListLoadPromise;
}

function startAuctionListPolling() {
    if (auctionListPollingTimer !== null) return;
    auctionListPollingTimer = window.setInterval(() => {
        if (auctionListLoadPromise === null) loadAuctions();
    }, AUCTION_POLL_INTERVAL_MS);
}

function stopAuctionListPolling() {
    if (auctionListPollingTimer !== null) {
        window.clearInterval(auctionListPollingTimer);
        auctionListPollingTimer = null;
    }
}

async function initializePage() {
    updateAuthenticationUI();
    if (getSessionToken()) {
        try {
            const data = await fetchAuthenticatedJson(`${API_BASE_URL}/profile`);
            if (data.profile && data.profile.userId && data.profile.role) {
                currentUser = data.profile;
                profileLoaded = false;
                updateAuthenticationUI();
            }
        } catch (error) {
            if (!error || typeof error.status !== 'number') {
                showLoginMessage('Could not restore your login session. Check the API connection.', true);
            }
        }
    }
    const connected = await checkApiHealth();
    const loaded = await loadAuctions();
    if (!connected || !loaded) setApiStatus(false);
    startAuctionListPolling();
}

initializePage();
