import { useState, useEffect } from 'react';
import { signInWithEmailAndPassword, signOut, onAuthStateChanged } from 'firebase/auth';
import { auth } from './firebaseConfig';
import ReportList from './components/ReportList';

function App() {
  const [user, setUser] = useState(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Login form state
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loginLoading, setLoginLoading] = useState(false);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (currentUser) => {
      if (currentUser) {
        // Get token result to check admin claim
        const tokenResult = await currentUser.getIdTokenResult();
        const adminClaim = tokenResult.claims.admin === true;

        if (adminClaim) {
          setUser(currentUser);
          setIsAdmin(true);
          setError(null);
        } else {
          setError('Access denied: Admin privileges required');
          await signOut(auth);
          setUser(null);
          setIsAdmin(false);
        }
      } else {
        setUser(null);
        setIsAdmin(false);
      }
      setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  const handleLogin = async (e) => {
    e.preventDefault();
    setError(null);
    setLoginLoading(true);

    try {
      const userCredential = await signInWithEmailAndPassword(auth, email, password);
      const tokenResult = await userCredential.user.getIdTokenResult();

      if (tokenResult.claims.admin !== true) {
        await signOut(auth);
        setError('Access denied: Admin privileges required');
      }
    } catch (err) {
      setError(err.message || 'Failed to sign in');
    } finally {
      setLoginLoading(false);
    }
  };

  const handleLogout = async () => {
    try {
      await signOut(auth);
      setEmail('');
      setPassword('');
    } catch (err) {
      setError('Failed to sign out');
    }
  };

  if (loading) {
    return (
      <div className="loading">
        <div className="spinner"></div>
        <p>Loading...</p>
      </div>
    );
  }

  if (!user || !isAdmin) {
    return (
      <div className="login-container">
        <div className="login-card">
          <h2>Liftrix Admin Dashboard</h2>
          <p>Sign in with admin credentials to access content moderation</p>

          {error && (
            <div className="error-message">
              {error}
            </div>
          )}

          <form onSubmit={handleLogin}>
            <div className="form-group">
              <label htmlFor="email">Email</label>
              <input
                type="email"
                id="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                disabled={loginLoading}
                placeholder="admin@liftrix.com"
              />
            </div>

            <div className="form-group">
              <label htmlFor="password">Password</label>
              <input
                type="password"
                id="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                disabled={loginLoading}
                placeholder="••••••••"
              />
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              disabled={loginLoading}
              style={{ width: '100%' }}
            >
              {loginLoading ? 'Signing in...' : 'Sign In'}
            </button>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div>
      <div className="header">
        <div className="container">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <h1>Content Moderation Dashboard</h1>
              <p>Signed in as {user.email}</p>
            </div>
            <button onClick={handleLogout} className="btn btn-secondary">
              Sign Out
            </button>
          </div>
        </div>
      </div>

      <div className="container">
        <ReportList />
      </div>
    </div>
  );
}

export default App;
