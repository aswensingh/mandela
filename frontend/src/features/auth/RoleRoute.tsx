import { Navigate, Outlet } from 'react-router-dom';
import { useAppSelector } from '@/app/hooks';
import type { UserRole } from './authSlice';

type RoleRouteProps = {
  roles: UserRole[];
};

export default function RoleRoute({ roles }: RoleRouteProps) {
  const user = useAppSelector((state) => state.auth.user);
  const accessToken = useAppSelector((state) => state.auth.accessToken);

  if (!accessToken) {
    return <Navigate to="/login" replace />;
  }
  if (!user || !roles.includes(user.role)) {
    return <Navigate to="/app" replace />;
  }
  return <Outlet />;
}
