const API_BASE_URL = 'http://127.0.0.1:8080/api';
const isLoginPage = document.body.dataset.page === 'login';

const apiStatus = document.querySelector('#api-status');
const auctionList = document.querySelector('#auction-list');
const auctionCount = document.querySelector('#auction-count');
const pageMessage = document.querySelector('#page-message');
const auctionSearchInput = document.querySelector('#auction-search');
const auctionStatusFilter = document.querySelector('#auction-status-filter');
const auctionConditionFilter = document.querySelector('#auction-condition-filter');
const auctionSortSelect = document.querySelector('#auction-sort');
const detailsSection = document.querySelector('#auction-details');
const detailsContent = document.querySelector('#details-content');
const bidForm = document.querySelector('#bid-form');
const bidAccessMessage = document.querySelector('#bid-access-message');
const bidAccount = document.querySelector('#bid-account');
const bidFields = bidForm?.querySelector('.form-fields');
const bidAmountInput = document.querySelector('#bid-amount');
const placeBidButton = document.querySelector('#place-bid-button');
const bidMessage = document.querySelector('#bid-message');
const bidRoleMessage = document.querySelector('#bid-role-message');
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
const profileAvatar = document.querySelector('#profile-avatar');
const profileMenuTrigger = document.querySelector('#profile-menu-trigger');
const profileMenuDropdown = document.querySelector('#profile-menu-dropdown');
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
const sellerAuctionsPanel = document.querySelector('#seller-auctions-panel');
const sellerAuctionsList = document.querySelector('#seller-auctions-list');
const sellerAuctionsEmpty = document.querySelector('#seller-auctions-empty');
const sellerAuctionsError = document.querySelector('#seller-auctions-error');
const buyerBidsPanel = document.querySelector('#buyer-bids-panel');
const buyerBidsList = document.querySelector('#buyer-bids-list');
const buyerBidsEmpty = document.querySelector('#buyer-bids-empty');
const buyerBidsError = document.querySelector('#buyer-bids-error');
const showCreateAuctionButton = document.querySelector('#show-create-auction-button');
const createAuctionForm = document.querySelector('#create-auction-form');
const createAuctionIdInput = document.querySelector('#create-auction-id');
const createProductIdInput = document.querySelector('#create-product-id');
const createProductNameInput = document.querySelector('#create-product-name');
const createProductDescriptionInput = document.querySelector('#create-product-description');
const createProductConditionInput = document.querySelector('#create-product-condition');
const createProductImageInput = document.querySelector('#create-product-image');
const createProductImageFilename = document.querySelector('#create-product-image-filename');
const createProductImagePreview = document.querySelector('#create-product-image-preview');
const removeProductImageButton = document.querySelector('#remove-product-image-button');
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
let sellerAuctionMessageTimer = null;
let createAuctionFormOpen = false;
let allAuctions = [];
let roleDashboardRequestInProgress = false;
let selectedProductPreviewUrl = null;
let auctionClosureInProgress = false;

if (isLoginPage) {
    loginForm.addEventListener('submit', submitLogin);
    registrationForm.addEventListener('submit', submitRegistration);
    document.querySelector('#show-register-button').addEventListener('click', () => setAuthMode('register'));
    document.querySelector('#show-login-button').addEventListener('click', () => setAuthMode('login'));
} else {
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
logoutButton.addEventListener('click', submitLogout);
profileMenuTrigger.addEventListener('click', () => {
    const isOpen = !profileMenuDropdown.hidden;
    profileMenuDropdown.hidden = isOpen;
    profileMenuTrigger.setAttribute('aria-expanded', String(!isOpen));
});
document.addEventListener('click', (event) => {
    if (!event.target.closest('.profile-menu')) closeProfileMenu();
});
document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeProfileMenu();
});
viewProfileButton.addEventListener('click', closeProfileMenu);
editProfileButton.addEventListener('click', closeProfileMenu);
logoutButton.addEventListener('click', closeProfileMenu);
profileForm.addEventListener('submit', submitProfileUpdate);
showCreateAuctionButton.addEventListener('click', () => {
    createAuctionFormOpen = !createAuctionFormOpen;
    sellerAuctionMessage.hidden = true;
    updateAuthenticationUI();
});
createAuctionForm.addEventListener('submit', submitAuctionCreation);
createProductImageInput.addEventListener('change', previewProductImage);
removeProductImageButton.addEventListener('click', removeSelectedProductImage);
cancelCreateAuctionButton.addEventListener('click', () => {
    createAuctionFormOpen = false;
    createAuctionForm.reset();
    clearProductImagePreview();
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
    clearProductImagePreview();
});
auctionSearchInput.addEventListener('input', renderFilteredAuctions);
auctionStatusFilter.addEventListener('change', renderFilteredAuctions);
auctionConditionFilter.addEventListener('change', renderFilteredAuctions);
auctionSortSelect.addEventListener('change', renderFilteredAuctions);
}

function closeProfileMenu() {
    if (!profileMenuDropdown || !profileMenuTrigger) return;
    profileMenuDropdown.hidden = true;
    profileMenuTrigger.setAttribute('aria-expanded', 'false');
}

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
            if (!isLoginPage) window.location.replace('login.html');
        }
        throw error;
    }
}

function clearBrowserSession() {
    sessionStorage.removeItem('auctionSessionToken');
    currentUser = null;
    profileLoaded = false;
    profileEditorOpen = false;
    if (bidAmountInput) bidAmountInput.value = '';
    if (bidMessage) clearBidMessage();
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
        if ([400, 409, 413].includes(error.status) && error.responseMessage) return error.responseMessage;
        if (error.status === 401) return 'Log in with a seller account to create an auction.';
        if (error.status === 403) return 'Only a seller can create an auction.';
        if (error.status === 500) {
            return error.responseMessage || 'The auction could not be created because of a server problem.';
        }
        return 'The auction could not be created. Check the details and try again.';
    }
    if (action === 'auction-close') {
        if (error.status === 401) return 'Log in again to end your auction.';
        if (error.status === 403) return 'You can only end your own auctions as a seller.';
        if (error.status === 404) return 'That auction could not be found.';
        if (error.status === 409) return 'This auction has already ended or expired.';
        if (error.status >= 500) return 'The auction could not be ended because of a server problem.';
        return 'The auction could not be ended. Please try again.';
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
    if (isLoginPage) {
        authOptions.hidden = false;
        loginForm.hidden = authMode !== 'login';
        registrationForm.hidden = authMode !== 'register';
        return;
    }
    const loggedIn = Boolean(currentUser);
    loggedInPanel.hidden = !loggedIn;
    profilePanel.hidden = !loggedIn || !profileLoaded;
    profileForm.hidden = !loggedIn || !profileEditorOpen;
    sellerDashboard.hidden = !isLoggedInSeller();
    sellerAuctionsPanel.hidden = !isLoggedInSeller();
    buyerBidsPanel.hidden = !isLoggedInBuyer();
    createAuctionForm.hidden = !isLoggedInSeller() || !createAuctionFormOpen;
    showCreateAuctionButton.disabled = auctionCreationInProgress;
    createAuctionButton.disabled = auctionCreationInProgress;
    if (loggedIn) {
        loggedInName.textContent = displayValue(currentUser.name);
        loggedInRole.textContent = `Role: ${String(currentUser.role).toUpperCase()}`;
        loggedInId.textContent = `User ID: ${currentUser.userId}`;
        const initial = String(currentUser.name || 'U').trim().charAt(0).toUpperCase() || 'U';
        profileAvatar.textContent = initial;
    } else {
        loggedInName.textContent = '';
        loggedInRole.textContent = '';
        loggedInId.textContent = '';
        profileAvatar.textContent = 'U';
        closeProfileMenu();
        profileLoaded = false;
        profileEditorOpen = false;
        profileMessage.hidden = true;
        profileEditMessage.hidden = true;
        profileForm.reset();
        createAuctionFormOpen = false;
        createAuctionForm.reset();
        sellerAuctionMessage.hidden = true;
        sellerAuctionsList.replaceChildren();
        buyerBidsList.replaceChildren();
        sellerAuctionsEmpty.hidden = true;
        sellerAuctionsError.hidden = true;
        buyerBidsEmpty.hidden = true;
        buyerBidsError.hidden = true;
    }

    const canBid = isLoggedInBuyer();
    bidFields.hidden = !canBid;
    bidRoleMessage.hidden = !loggedIn || canBid;
    bidRoleMessage.textContent = loggedIn && !canBid
        ? 'Buyer account required to place bids.' : '';
    bidAccessMessage.hidden = !loggedIn || !canBid;
    bidAccessMessage.textContent = canBid
        ? 'Your logged-in Buyer account will be used for this bid.' : '';
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
    const image = createProductImageInput.files && createProductImageInput.files[0];
    const endTime = createAuctionEndTimeInput.value;
    const incrementText = createAuctionIncrementInput.value.trim();
    if (!auctionId || !productId || !productName || !description || !condition
            || !endTime || !incrementText || !image) {
        showAccountMessage(sellerAuctionMessage, 'Complete every auction and product field.', true);
        return;
    }
    if (!['Poor', 'Average', 'Good', 'Excellent'].includes(condition)) {
        showAccountMessage(sellerAuctionMessage, 'Choose Poor, Average, Good, or Excellent.', true);
        return;
    }
    if (!/\.(jpe?g|png|webp)$/i.test(image.name || '')
            || image.size === 0 || image.size > 5 * 1024 * 1024) {
        showAccountMessage(sellerAuctionMessage,
            'Choose a JPG, JPEG, PNG, or WEBP image larger than 0 bytes and no larger than 5 MB.', true);
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
        const formData = new FormData();
        formData.append('auctionId', auctionId);
        formData.append('productId', productId);
        formData.append('productName', productName);
        formData.append('description', description);
        formData.append('condition', condition);
        formData.append('endTime', endTime);
        formData.append('minimumBidIncrement', incrementText);
        formData.append('productImage', image);
        const data = await fetchAuthenticatedJson(`${API_BASE_URL}/auctions`, {
            method: 'POST',
            body: formData
        });
        clearProductImagePreview();
        createAuctionForm.reset();
        createAuctionFormOpen = false;
        updateAuthenticationUI();
        showAccountMessage(sellerAuctionMessage,
            data.message
                ? `${data.message} ${productName} (${auctionId}) ends ${formatDate(endTime)}; minimum increment ${formatAmount(minimumBidIncrement)}.`
                : `Auction ${auctionId} created for ${productName}. Ends ${formatDate(endTime)}; minimum increment ${formatAmount(minimumBidIncrement)}.`,
            false);
        if (sellerAuctionMessageTimer !== null) window.clearTimeout(sellerAuctionMessageTimer);
        sellerAuctionMessageTimer = window.setTimeout(() => {
            sellerAuctionMessage.hidden = true;
            sellerAuctionMessageTimer = null;
        }, 6000);
        const refreshed = await loadAuctions();
        await loadRoleDashboard();
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

function previewProductImage() {
    const image = createProductImageInput.files && createProductImageInput.files[0];
    clearProductImagePreview();
    if (!image) return;
    const hasAllowedExtension = /\.(jpe?g|png|webp)$/i.test(image.name || '');
    if (!hasAllowedExtension || image.size === 0 || image.size > 5 * 1024 * 1024) {
        createProductImageInput.value = '';
        showAccountMessage(sellerAuctionMessage, !hasAllowedExtension
            ? 'Choose a JPG, JPEG, PNG, or WEBP image.'
            : 'Choose an image larger than 0 bytes and no larger than 5 MB.', true);
        return;
    }
    selectedProductPreviewUrl = URL.createObjectURL(image);
    createProductImageFilename.textContent = image.name;
    createProductImageFilename.hidden = false;
    createProductImagePreview.onerror = () => {
        createProductImageInput.value = '';
        clearProductImagePreview();
        showAccountMessage(sellerAuctionMessage,
            'The selected file could not be opened as an image. Choose a valid image file.', true);
    };
    createProductImagePreview.src = selectedProductPreviewUrl;
    createProductImagePreview.hidden = false;
    removeProductImageButton.hidden = false;
    sellerAuctionMessage.hidden = true;
}

function clearProductImagePreview() {
    if (selectedProductPreviewUrl) URL.revokeObjectURL(selectedProductPreviewUrl);
    selectedProductPreviewUrl = null;
    createProductImageFilename.textContent = '';
    createProductImageFilename.hidden = true;
    createProductImagePreview.removeAttribute('src');
    createProductImagePreview.onerror = null;
    createProductImagePreview.hidden = true;
    removeProductImageButton.hidden = true;
}

function removeSelectedProductImage() {
    createProductImageInput.value = '';
    clearProductImagePreview();
}

function setAuthMode(mode) {
    authMode = mode;
    loginMessage.hidden = true;
    registrationMessage.hidden = true;
    updateAuthenticationUI();
}

function showLoginMessage(message, isError = true) {
    if (!loginMessage) return;
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
        loginPasswordInput.value = '';
        if (isLoginPage) {
            window.location.replace('index.html');
            return;
        }
        updateAuthenticationUI();
        loadRoleDashboard();
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
        window.location.replace('login.html');
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
        document.querySelector('#account-section').scrollIntoView({ behavior: 'smooth', block: 'start' });
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
        document.querySelector('#account-section').scrollIntoView({ behavior: 'smooth', block: 'start' });
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

function getAuctionStatusDisplay(status) {
    const normalized = String(status || '').toLowerCase();
    if (normalized === 'open') return { label: 'Active', className: 'open' };
    if (normalized === 'expired') return { label: 'Expired', className: 'expired' };
    if (normalized.includes('closed')) return { label: 'Closed', className: 'closed' };
    return { label: displayValue(status, 'Unknown'), className: 'unknown' };
}

function renderAuction(auction) {
    const product = auction.product || {};
    const highestBid = auction.currentHighestBid;
    const status = getAuctionStatusDisplay(auction.status);
    const auctionEnded = status.className !== 'open';
    const card = createElement('article', 'auction-card');
    const topLine = createElement('div', 'card-topline');
    topLine.append(createElement('span', 'product-condition', displayValue(product.condition, 'Condition TBD')));
    topLine.append(createElement('span', `auction-status ${status.className}`, status.label));

    const title = createElement('h3', '', displayValue(product.name, 'Untitled product'));
    const description = createElement('p', 'product-description', displayValue(product.description, 'No description provided.'));
    const facts = createElement('div', 'auction-facts');
    addFact(facts, auctionEnded ? 'Final winning bid' : 'Current highest bid',
        formatAmount(getHighestBidAmount(auction)));
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

    addProductImage(card, product, false);
    card.append(topLine, title, description, facts, actions);
    if (isLoggedInSeller() && auction.status === 'open' && auction.seller
            && String(auction.seller.userId) === String(currentUser.userId)) {
        const endButton = createElement('button', 'button end-auction-button', 'End auction');
        endButton.type = 'button';
        endButton.disabled = auctionClosureInProgress;
        endButton.addEventListener('click', () => endAuction(auction.auctionId));
        actions.append(endButton);
    }
    return card;
}

function addProductImage(container, product, large) {
    const imageUrl = productImageUrl(product);
    if (!imageUrl) {
        const placeholder = createElement('div',
            large ? 'product-image-placeholder product-image-large' : 'product-image-placeholder',
            'Product image unavailable');
        placeholder.setAttribute('aria-label', 'Product image unavailable');
        container.append(placeholder);
        return;
    }
    const image = createElement('img', large ? 'auction-product-image product-image-large'
        : 'auction-product-image');
    image.src = imageUrl;
    image.alt = `${displayValue(product.name, 'Auction product')} image`;
    image.loading = 'lazy';
    image.addEventListener('error', () => {
        image.replaceWith(createElement('div',
            large ? 'product-image-placeholder product-image-large' : 'product-image-placeholder',
            'Product image unavailable'));
    }, { once: true });
    container.append(image);
}

function productImageUrl(product) {
    if (!product || typeof product.imageUrl !== 'string') return null;
    try {
        const image = new URL(product.imageUrl, API_BASE_URL);
        const api = new URL(API_BASE_URL);
        if (image.origin !== api.origin
                || !/^\/api\/products\/images\/[0-9a-f-]+\.(?:jpg|png|webp)$/i.test(image.pathname)) {
            return null;
        }
        return image.href;
    } catch {
        return null;
    }
}

async function endAuction(auctionId) {
    if (!isLoggedInSeller() || auctionClosureInProgress) return;
    if (!window.confirm('Are you sure you want to end this auction? Buyers will no longer be able to place bids.')) {
        return;
    }
    auctionClosureInProgress = true;
    try {
        const result = await fetchAuthenticatedJson(
            `${API_BASE_URL}/auctions/${encodeURIComponent(auctionId)}/close`, { method: 'POST' });
        await loadAuctions();
        await loadRoleDashboard();
        if (String(selectedAuctionId) === String(auctionId)) {
            const data = await fetchJson(`${API_BASE_URL}/auctions/${encodeURIComponent(auctionId)}`);
            if (data.auction) {
                renderDetails(data.auction);
                noteAuctionUpdated();
            }
        }
        showMessage(result.message || 'Auction ended successfully.', 'success');
    } catch (error) {
        showMessage(friendlyApiError(error, 'auction-close'), 'error');
    } finally {
        auctionClosureInProgress = false;
    }
}

function renderAuctions(data) {
    if (!data || !Array.isArray(data.auctions)) {
        throw new Error('The auction server returned auction data in an unexpected format.');
    }

    allAuctions = data.auctions.slice();
    updateConditionOptions(allAuctions);
    renderFilteredAuctions();
}

function updateConditionOptions(auctions) {
    const selectedCondition = auctionConditionFilter.value;
    const allowedConditions = ['all', 'poor', 'average', 'good', 'excellent'];
    auctionConditionFilter.value = allowedConditions.includes(selectedCondition.toLowerCase())
        ? selectedCondition.toLowerCase() : 'all';
}

function renderFilteredAuctions() {
    const search = auctionSearchInput.value.trim().toLowerCase();
    const statusFilter = auctionStatusFilter.value;
    const conditionFilter = auctionConditionFilter.value.toLowerCase();
    const sortBy = auctionSortSelect.value;
    const filtered = allAuctions.filter((auction) => {
        const product = auction.product || {};
        const status = String(auction.status || '').toLowerCase();
        const condition = String(product.condition || '').toLowerCase();
        const nameMatches = String(product.name || '').toLowerCase().includes(search);
        const statusMatches = statusFilter === 'all'
            || (statusFilter === 'active' && status === 'open')
            || (statusFilter === 'ended' && status !== 'open');
        const conditionMatches = conditionFilter === 'all' || condition === conditionFilter;
        return nameMatches && statusMatches && conditionMatches;
    });

    const sorted = filtered.slice();
    if (sortBy === 'highest-bid') {
        sorted.sort((first, second) => (getHighestBidAmount(second) || 0)
            - (getHighestBidAmount(first) || 0));
    } else if (sortBy === 'lowest-bid') {
        sorted.sort((first, second) => (getHighestBidAmount(first) || 0)
            - (getHighestBidAmount(second) || 0));
    } else {
        sorted.sort((first, second) => getEndTimestamp(first) - getEndTimestamp(second));
    }

    auctionList.replaceChildren();
    auctionCount.textContent = sorted.length === allAuctions.length
        ? `${allAuctions.length} ${allAuctions.length === 1 ? 'auction' : 'auctions'}`
        : `${sorted.length} of ${allAuctions.length} auctions`;
    if (sorted.length === 0) {
        showMessage(allAuctions.length === 0
            ? 'There are no auctions available right now.'
            : 'No auctions match your search and filters.', 'empty');
        return;
    }

    hideMessage();
    sorted.forEach((auction) => auctionList.append(renderAuction(auction)));
}

function getEndTimestamp(auction) {
    const timestamp = parseApiDateTime(auction.endTime).getTime();
    return Number.isNaN(timestamp) ? Number.POSITIVE_INFINITY : timestamp;
}

async function loadRoleDashboard() {
    if (!currentUser || roleDashboardRequestInProgress) return;
    const isSeller = isLoggedInSeller();
    const isBuyer = isLoggedInBuyer();
    if (!isSeller && !isBuyer) return;

    const requestedUserId = String(currentUser.userId);
    roleDashboardRequestInProgress = true;
    try {
        if (isSeller) {
            sellerAuctionsError.hidden = true;
            sellerAuctionsEmpty.hidden = true;
            const data = await fetchAuthenticatedJson(`${API_BASE_URL}/my-auctions`);
            if (!currentUser || String(currentUser.userId) !== requestedUserId
                    || !isLoggedInSeller()) return;
            if (!Array.isArray(data.auctions)) throw new Error('Unexpected seller auction data.');
            sellerAuctionsList.replaceChildren();
            sellerAuctionsEmpty.hidden = data.auctions.length !== 0;
            data.auctions.forEach((auction) => sellerAuctionsList.append(renderAuction(auction)));
        } else {
            buyerBidsError.hidden = true;
            buyerBidsEmpty.hidden = true;
            const data = await fetchAuthenticatedJson(`${API_BASE_URL}/my-bids`);
            if (!currentUser || String(currentUser.userId) !== requestedUserId
                    || !isLoggedInBuyer()) return;
            if (!Array.isArray(data.auctions)) throw new Error('Unexpected buyer bid data.');
            buyerBidsList.replaceChildren();
            buyerBidsEmpty.hidden = data.auctions.length !== 0;
            data.auctions.forEach((entry) => buyerBidsList.append(renderMyBid(entry)));
        }
    } catch (error) {
        if (!error || error.status !== 401) {
            if (isSeller) sellerAuctionsError.hidden = false;
            if (isBuyer) buyerBidsError.hidden = false;
        }
    } finally {
        roleDashboardRequestInProgress = false;
    }
}

function renderMyBid(entry) {
    const auction = entry.auction || {};
    const highestBid = auction.currentHighestBid;
    const status = String(auction.status || '').toLowerCase();
    const statusDisplay = getAuctionStatusDisplay(auction.status);
    const ended = status !== 'open';
    const buyerIsLeading = Boolean(highestBid
        && String(highestBid.buyerId) === String(currentUser.userId));
    const buyerWon = Boolean(ended && auction.winner
        && String(auction.winner.userId) === String(currentUser.userId));
    const card = createElement('article', 'auction-card');
    const topLine = createElement('div', 'card-topline');
    topLine.append(createElement('span', 'product-condition',
        displayValue(auction.product && auction.product.condition, 'Condition TBD')));
    topLine.append(createElement('span', `auction-status ${statusDisplay.className}`,
        statusDisplay.label));
    card.append(topLine);
    addProductImage(card, auction.product || {}, false);
    card.append(createElement('h4', '', displayValue(
        auction.product && auction.product.name, 'Untitled product')));
    card.append(createElement('p', 'auction-id', `Auction ${displayValue(auction.auctionId, '—')}`));

    const facts = createElement('div', 'auction-facts');
    addFact(facts, 'Your highest bid', formatAmount(entry.myHighestBid && entry.myHighestBid.amount));
    addFact(facts, 'Current highest bid', formatAmount(getHighestBidAmount(auction)));
    addFact(facts, 'Auction ends', formatDate(auction.endTime));
    addFact(facts, 'Your position', ended
        ? (buyerWon ? 'You won' : 'You lost')
        : (buyerIsLeading ? 'Currently leading' : 'Not currently leading'));
    card.append(facts);

    const actions = createElement('div', 'card-actions');
    const viewButton = createElement('button', 'button', 'View auction');
    viewButton.type = 'button';
    viewButton.addEventListener('click', () => loadAuctionDetails(auction.auctionId));
    actions.append(viewButton);
    card.append(actions);
    return card;
}

function appendDetail(container, label, value) {
    const item = createElement('div');
    item.append(createElement('dt', '', label));
    const detailValue = createElement('dd', '', value);
    item.append(detailValue);
    container.append(item);
    return detailValue;
}

function renderBidHistory(bids) {
    const section = createElement('section', 'bid-history');
    section.append(createElement('h4', '', 'Bid history'));
    if (!Array.isArray(bids) || bids.length === 0) {
        section.append(createElement('p', 'form-note', 'No bids have been placed yet.'));
        return section;
    }

    const newestFirst = bids.slice().sort((first, second) => {
        const firstTime = parseApiDateTime(first.timestamp).getTime();
        const secondTime = parseApiDateTime(second.timestamp).getTime();
        const byMilliseconds = (Number.isNaN(secondTime) ? 0 : secondTime)
            - (Number.isNaN(firstTime) ? 0 : firstTime);
        return byMilliseconds || String(second.timestamp || '').localeCompare(
            String(first.timestamp || ''));
    });
    const table = createElement('table', 'bid-history-table');
    const header = document.createElement('thead');
    const headerRow = document.createElement('tr');
    ['Bidder', 'Bid amount', 'Date and time'].forEach((label) => {
        headerRow.append(createElement('th', '', label));
    });
    header.append(headerRow);
    const body = document.createElement('tbody');
    newestFirst.forEach((bid) => {
        const entry = document.createElement('tr');
        const bidder = createElement('th', '', displayValue(bid.buyerName, 'Unknown bidder'));
        bidder.scope = 'row';
        const amount = createElement('td', '', formatAmount(bid.amount));
        const timeCell = document.createElement('td');
        const timestamp = createElement('time', '', formatDate(bid.timestamp));
        if (typeof bid.timestamp === 'string') timestamp.dateTime = bid.timestamp;
        timeCell.append(timestamp);
        entry.append(bidder, amount, timeCell);
        body.append(entry);
    });
    table.append(header, body);
    section.append(table);
    return section;
}

function renderDetails(auction, shouldScroll = false) {
    const product = auction.product || {};
    const highestBid = auction.currentHighestBid;
    selectedEndTime = auction.endTime || null;
    detailsContent.replaceChildren();
    addProductImage(detailsContent, product, true);
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
    detailsContent.append(renderBidHistory(auction.bids));
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
            ? 'Buyer account required to place bids.'
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
        await loadRoleDashboard();

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
        if (auctionListLoadPromise === null) {
            loadAuctions().then(() => loadRoleDashboard());
        }
    }, AUCTION_POLL_INTERVAL_MS);
}

function stopAuctionListPolling() {
    if (auctionListPollingTimer !== null) {
        window.clearInterval(auctionListPollingTimer);
        auctionListPollingTimer = null;
    }
}

async function initializeLoginPage() {
    updateAuthenticationUI();
    const connected = await checkApiHealth();
    if (getSessionToken()) {
        try {
            const data = await fetchAuthenticatedJson(`${API_BASE_URL}/profile`);
            if (data.profile && data.profile.userId && data.profile.role) {
                window.location.replace('index.html');
                return;
            }
            sessionStorage.removeItem('auctionSessionToken');
        } catch (error) {
            if (!error || typeof error.status !== 'number') {
                showLoginMessage('Could not restore your login session. Check the API connection.', true);
            }
        }
    }
    if (!connected) showLoginMessage('The auction service is unavailable. Try again when it is online.', true);
}

async function initializeDashboardPage() {
    if (!getSessionToken()) {
        window.location.replace('login.html');
        return;
    }
    try {
        const data = await fetchAuthenticatedJson(`${API_BASE_URL}/profile`);
        if (data.profile && data.profile.userId && data.profile.role) {
            currentUser = data.profile;
            profileLoaded = false;
        } else {
            sessionStorage.removeItem('auctionSessionToken');
            window.location.replace('login.html');
            return;
        }
    } catch (error) {
        if (error && typeof error.status === 'number') return;
        showMessage('Could not verify your account because the auction service is unavailable.', 'error');
        setApiStatus(false);
        document.body.classList.remove('auth-pending');
        return;
    }
    updateAuthenticationUI();
    document.body.classList.remove('auth-pending');
    const connected = await checkApiHealth();
    const loaded = await loadAuctions();
    if (currentUser) await loadRoleDashboard();
    if (!connected || !loaded) setApiStatus(false);
    startAuctionListPolling();
}

if (isLoginPage) initializeLoginPage();
else initializeDashboardPage();
