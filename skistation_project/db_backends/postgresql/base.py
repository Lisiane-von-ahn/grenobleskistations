from django.db.backends.postgresql.base import DatabaseWrapper as BaseDatabaseWrapper
from django.db.utils import NotSupportedError
import psycopg2
import re

class DatabaseWrapper(BaseDatabaseWrapper):
    def get_new_connection(self, conn_params):
        conn = psycopg2.connect(**conn_params)
        self.connection = conn
        return conn

    def get_server_version(self):
        """
        Return the version of the connected PostgreSQL server as a tuple of integers
        representing the version (e.g. 9.4.5 -> (9, 4, 5)).
        """
        if not hasattr(self, 'connection'):
            raise NotSupportedError("PostgreSQL connection not established")
        version_str = self.connection.server_version
        version = re.match(r"(\d+)\.(\d+)\.(\d+)", version_str)
        return tuple(map(int, version.groups()))

    def init_connection_state(self):
        """
        Initializes the connection state, called by the Django ORM.
        """
        pass  # You can override it to customize the initialization behavior

    def _is_usable(self):
        """
        Checks if the database is usable for the Django ORM.
        """
        try:
            self.connection.cursor().execute('SELECT 1')
            return True
        except:
            return False
