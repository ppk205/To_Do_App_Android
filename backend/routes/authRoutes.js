const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const { registerValidation, loginValidation, validate } = require('../middleware/validation');

// Register route
router.post('/register', registerValidation, validate, authController.register);

// Login route
router.post('/login', loginValidation, validate, authController.login);

module.exports = router;

